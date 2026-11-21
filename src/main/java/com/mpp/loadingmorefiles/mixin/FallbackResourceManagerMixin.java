package com.mpp.loadingmorefiles.mixin;

import com.mpp.loadingmorefiles.LoadingMoreFiles;
import com.mpp.loadingmorefiles.format.ResourceFormats;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches the shared resource manager that a {@code .jpg}/{@code .jpeg} file is a valid answer
 * to a {@code textures/block/stone.png} lookup.
 *
 * <p>{@code FallbackResourceManager} is the class that resolves a resource name inside one
 * namespace by walking the pack layers from the highest priority pack down. It is used by both
 * the client resource manager and the server data manager, which is why the fix lives here and
 * not in the texture loader: every consumer of a texture (block/item atlas, GUI, fonts, mod
 * textures) goes through these two methods.</p>
 *
 * <p>Two operations are hooked:</p>
 * <ul>
 *   <li>{@link #loadingmorefiles$resolveAliasedFile} -- a single lookup. When the requested
 *       {@code .png} does not exist in a pack layer, that same layer is asked for the registered
 *       aliases before moving on, so an alternative file in a high priority pack still beats a
 *       vanilla file in a lower priority pack, exactly like a {@code .png} would.</li>
 *   <li>{@link #loadingmorefiles$listAliasedFiles} / {@link #loadingmorefiles$revealAliasedFiles}
 *       -- directory listings, used by the texture atlas to discover block and item sprites.
 *       Alternative files are listed under their canonical {@code .png} name and the alias name
 *       is dropped, so the atlas never sees a sprite called {@code foo.jpg}.</li>
 * </ul>
 *
 * <p>Nothing else changes: the returned {@link Resource} keeps the canonical name, metadata is
 * read from a sidecar next to the file that was actually found, and the vanilla first choice
 * (the requested {@code .png}) always wins inside a pack.</p>
 */
@Mixin(FallbackResourceManager.class)
public abstract class FallbackResourceManagerMixin {

    /**
     * Replaces the vanilla lookup with an alias-aware one for paths the format registry knows
     * about. Vanilla is left completely untouched for every other resource.
     */
    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void loadingmorefiles$resolveAliasedFile(ResourceLocation location,
                                                     CallbackInfoReturnable<Optional<Resource>> cir) {
        FallbackResourceManagerAccessor self = (FallbackResourceManagerAccessor) this;
        PackType type = self.loadingmorefiles$type();
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        List<ResourceLocation> candidates = ResourceFormats.aliasCandidates(location);
        if (candidates.isEmpty()) {
            return;
        }

        List<?> layers = self.loadingmorefiles$fallbacks();

        for (int i = layers.size() - 1; i >= 0; --i) {
            FallbackResourceManagerPackEntryAccessor entry =
                    (FallbackResourceManagerPackEntryAccessor) layers.get(i);

            PackResources pack = entry.loadingmorefiles$resources();
            if (pack != null) {
                IoSupplier<InputStream> stream = pack.getResource(type, location);
                ResourceLocation found = location;
                if (stream == null) {
                    for (ResourceLocation candidate : candidates) {
                        stream = pack.getResource(type, candidate);
                        if (stream != null) {
                            found = candidate;
                            break;
                        }
                    }
                }

                if (stream != null) {
                    ResourceFormats.reportServedFrom(location, found);
                    cir.setReturnValue(Optional.of(
                            ResourceFormats.canonicalResource(location, found, pack, stream,
                                    this.loadingmorefiles$metadata(type, found, i), sibling -> pack.getResource(type, sibling))));
                    return;
                }
            }

            Predicate<ResourceLocation> filter = entry.loadingmorefiles$filter();
            if (filter != null && filter.test(location)) {
                // Vanilla stops at the first pack whose filter denies the path; a lower priority
                // pack must not provide the file in that case, not even in another format.
                LoadingMoreFiles.LOGGER.debug("Resource {} is hidden by a pack filter", location);
                cir.setReturnValue(Optional.empty());
                return;
            }
        }

        cir.setReturnValue(Optional.empty());
    }

    /**
     * Metadata for a resource that may have been served from another file: a
     * {@code <file>.mcmeta} next to the file that was actually found, searched from the top of
     * the pack stack down to the layer that produced the file. Mirrors vanilla's
     * {@code createStackMetadataFinder}, which is private.
     */
    private IoSupplier<ResourceMetadata> loadingmorefiles$metadata(PackType type, ResourceLocation location,
                                                                   int packIndex) {
        return () -> {
            ResourceLocation metadataLocation = location.withPath(location.getPath() + ".mcmeta");
            List<?> layers = ((FallbackResourceManagerAccessor) this).loadingmorefiles$fallbacks();

            for (int i = layers.size() - 1; i >= packIndex; --i) {
                FallbackResourceManagerPackEntryAccessor entry =
                        (FallbackResourceManagerPackEntryAccessor) layers.get(i);

                PackResources pack = entry.loadingmorefiles$resources();
                if (pack != null) {
                    IoSupplier<InputStream> metadata = pack.getResource(type, metadataLocation);
                    if (metadata != null) {
                        try (InputStream stream = metadata.get()) {
                            return ResourceMetadata.fromJsonStream(stream);
                        }
                    }
                }

                Predicate<ResourceLocation> filter = entry.loadingmorefiles$filter();
                if (filter != null && filter.test(metadataLocation)) {
                    break;
                }
            }

            return ResourceMetadata.EMPTY;
        };
    }

    /**
     * Widens the listing predicate so packs may report {@code .jpg}/{@code .jpeg} files for a
     * directory that is being listed for its {@code .png} sprites.
     */
    @ModifyVariable(method = "listResources", at = @At("HEAD"), argsOnly = true)
    private Predicate<ResourceLocation> loadingmorefiles$listAliasedFiles(
            @Nullable Predicate<ResourceLocation> predicate) {
        if (predicate == null || ((FallbackResourceManagerAccessor) this).loadingmorefiles$type() != PackType.CLIENT_RESOURCES) {
            return predicate;
        }

        return location -> {
            if (predicate.test(location)) {
                return true;
            }
            ResourceLocation canonical = ResourceFormats.canonicalLocation(location);
            return canonical != null && predicate.test(canonical);
        };
    }

    /**
     * Renames listed alternative files to the canonical name they stand in for. The entry of the
     * most important pack wins, and inside one pack the real file always wins over an alias; the
     * alias name is dropped either way, so the atlas never sees a sprite called {@code foo.jpg}.
     * The map is only copied when an alias is actually present.
     */
    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void loadingmorefiles$revealAliasedFiles(String path, Predicate<ResourceLocation> predicate,
                                                     CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        Map<ResourceLocation, Resource> listed = cir.getReturnValue();
        if (listed == null || listed.isEmpty()
                || ((FallbackResourceManagerAccessor) this).loadingmorefiles$type() != PackType.CLIENT_RESOURCES) {
            return;
        }

        Set<ResourceLocation> canonicalNames = new HashSet<>();
        for (ResourceLocation location : listed.keySet()) {
            ResourceLocation canonical = ResourceFormats.canonicalLocation(location);
            if (canonical != null) {
                canonicalNames.add(canonical);
            }
        }
        if (canonicalNames.isEmpty()) {
            return;
        }

        Map<ResourceLocation, Resource> renamed = new TreeMap<>(listed);
        for (ResourceLocation canonical : canonicalNames) {
            Resource winner = listed.get(canonical);
            ResourceLocation found = canonical;
            int layer = winner == null ? -1 : this.loadingmorefiles$layer(winner);
            for (ResourceLocation candidate : ResourceFormats.aliasCandidates(canonical)) {
                Resource alternative = renamed.remove(candidate);
                if (alternative != null) {
                    int candidateLayer = this.loadingmorefiles$layer(alternative);
                    if (winner == null || candidateLayer > layer) {
                        winner = alternative;
                        layer = candidateLayer;
                        found = candidate;
                    }
                }
            }
            if (!found.equals(canonical)) {
                ResourceFormats.reportServedFrom(canonical, found);
                PackResources pack = winner.source();
                renamed.put(canonical, ResourceFormats.canonicalResource(canonical, found, pack, winner::open,
                        winner::metadata, sibling -> pack.getResource(PackType.CLIENT_RESOURCES, sibling)));
            }
        }
        cir.setReturnValue(renamed);
    }

    /**
     * Index of the pack layer a listed resource came from: the last layer wins, so a higher
     * number is a more important pack. Returns {@code -1} if the layer cannot be identified.
     */
    private int loadingmorefiles$layer(Resource resource) {
        PackResources source = resource.source();
        List<?> layers = ((FallbackResourceManagerAccessor) this).loadingmorefiles$fallbacks();
        for (int i = layers.size() - 1; i >= 0; --i) {
            FallbackResourceManagerPackEntryAccessor entry =
                    (FallbackResourceManagerPackEntryAccessor) layers.get(i);
            if (entry.loadingmorefiles$resources() == source) {
                return i;
            }
        }
        return -1;
    }
}
