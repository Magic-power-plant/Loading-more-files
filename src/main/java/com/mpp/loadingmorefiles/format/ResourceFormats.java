package com.mpp.loadingmorefiles.format;

import com.mpp.loadingmorefiles.LoadingMoreFiles;
import com.mpp.loadingmorefiles.config.LmfConfig;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

/**
 * Registry of {@link FormatAlias}es, and the only place that decides which file the game
 * reads when it asks for a resource that packs may have stored under another name.
 *
 * <p>Threading: registration happens once during mod construction, while lookups run
 * concurrently on reload worker threads. The alias list is therefore published as an immutable
 * snapshot and replaced wholesale on registration.</p>
 */
public final class ResourceFormats {

    private static volatile List<FormatAlias> aliases = List.of();
    private static final ResourceDecoder JPEG = new JpegAlphaDecoder();
    private static final FormatAlias WEBP = new FormatAlias("webp", ".png", ".webp", "textures/", new WebpDecoder());
    private static final FormatAlias GIF = new FormatAlias("gif", ".png", ".gif", "textures/", new GifDecoder());
    private static final FormatAlias TGA = new FormatAlias("tga", ".png", ".tga", "textures/",
            new TgaDecoder());

    private ResourceFormats() {}

    /**
     * Registers the image formats the mod ships with. Idempotent, so it is safe to call twice.
     *
     * <p>JPEG is the first format family with real support: {@code .jpg} and {@code .jpeg} files
     * can answer any {@code textures/block/stone.png} request.</p>
     */
    public static void registerImageFormats() {
        register(new FormatAlias("jpeg", ".png", ".jpg", "textures/", JPEG));
        register(new FormatAlias("jpeg", ".png", ".jpeg", "textures/", JPEG));
        register(WEBP);
        register(GIF);
        register(TGA);
    }

    public static void registerSoundFormats() {
        for (SoundResource.Encoding encoding : SoundResource.Encoding.values()) {
            String extension = encoding.name().toLowerCase(java.util.Locale.ROOT);
            register(new FormatAlias(extension, ".ogg", "." + extension, "sounds/", encoding));
        }
    }

    /**
     * Adds an alias. Duplicate registrations are ignored, so pack-provided or mod-provided
     * registrations may be repeated safely.
     */
    public static synchronized void register(FormatAlias alias) {
        Objects.requireNonNull(alias, "alias");
        if (aliases.contains(alias)) {
            return;
        }

        List<FormatAlias> updated = new ArrayList<>(aliases.size() + 1);
        updated.addAll(aliases);
        updated.add(alias);
        aliases = List.copyOf(updated);

        LoadingMoreFiles.LOGGER.debug("Registered format alias {} -> {}{}",
                alias.aliasExtension(), alias.pathPrefix(), alias.canonicalExtension());
    }

    /** The registered aliases, oldest first. Read-only. */
    public static List<FormatAlias> registered() {
        return aliases;
    }

    /**
     * Files that may stand in for {@code requested}, in the order they should be tried.
     *
     * <p>An empty result means "not our business": aliasing is disabled, the path is not a
     * texture, or the extension is not one we can serve. Callers use that to leave the vanilla
     * code path completely untouched.</p>
     */
    public static List<ResourceLocation> aliasCandidates(ResourceLocation requested) {
        List<FormatAlias> current = aliases;
        if (current.isEmpty() || !LmfConfig.aliasesEnabled()) {
            return List.of();
        }

        String path = requested.getPath();
        List<ResourceLocation> candidates = null;
        for (FormatAlias alias : current) {
            if (!alias.accepts(path)) {
                continue;
            }
            if (candidates == null) {
                candidates = new ArrayList<>(2);
            }
            candidates.add(requested.withPath(alias.toAliasPath(path)));
        }
        return candidates == null ? List.of() : candidates;
    }

    /**
     * The canonical name an alternative file is served as, or {@code null} when the location
     * is not an alias file (or aliasing is disabled).
     */
    @Nullable
    public static ResourceLocation canonicalLocation(ResourceLocation alternative) {
        List<FormatAlias> current = aliases;
        if (current.isEmpty() || !LmfConfig.aliasesEnabled()) {
            return null;
        }

        String path = alternative.getPath();
        for (FormatAlias alias : current) {
            if (alias.isAliasPath(path)) {
                return alternative.withPath(alias.toCanonicalPath(path));
            }
        }
        return null;
    }

    public static IoSupplier<InputStream> canonicalStream(ResourceLocation requested, ResourceLocation found,
                                                           IoSupplier<InputStream> source) {
        return canonicalStream(requested, found, source, unused -> null);
    }

    public static IoSupplier<InputStream> canonicalStream(ResourceLocation requested, ResourceLocation found,
                                                          IoSupplier<InputStream> source,
                                                          Function<ResourceLocation, IoSupplier<InputStream>> lookup) {
        return decodedStream(requested, found, source, decoderFor(requested, found, lookup));
    }

    public static Resource canonicalResource(ResourceLocation requested, ResourceLocation found, PackResources pack,
                                             IoSupplier<InputStream> source, IoSupplier<ResourceMetadata> metadata,
                                             Function<ResourceLocation, IoSupplier<InputStream>> lookup) {
        ResourceDecoder decoder = decoderFor(requested, found, lookup);
        return decoder.createResource(pack, source, decodedStream(requested, found, source, decoder), metadata);
    }

    private static ResourceDecoder decoderFor(ResourceLocation requested, ResourceLocation found,
                                              Function<ResourceLocation, IoSupplier<InputStream>> lookup) {
        if (!requested.equals(found)) {
            for (FormatAlias alias : aliases) {
                if (alias.accepts(requested.getPath()) && alias.isAliasPath(found.getPath())) {
                    return alias.decoder().withSidecars(
                            suffix -> lookup.apply(found.withPath(found.getPath() + suffix)));
                }
            }
        }
        return ResourceDecoder.IDENTITY;
    }

    private static IoSupplier<InputStream> decodedStream(ResourceLocation requested, ResourceLocation found,
                                                         IoSupplier<InputStream> source, ResourceDecoder decoder) {
        if (decoder == ResourceDecoder.IDENTITY) {
            return source;
        }
        return () -> {
            try {
                return decoder.decode(source.get());
            } catch (IOException | RuntimeException exception) {
                throw new IOException("Could not decode " + found + " as " + requested, exception);
            }
        };
    }

    /** Records that {@code requested} was served from another file, for pack debugging. */
    public static void reportServedFrom(ResourceLocation requested, ResourceLocation served) {
        if (served.equals(requested)) {
            return;
        }

        if (LmfConfig.logAliasedResources()) {
            LoadingMoreFiles.LOGGER.info("Serving {} from {}", requested, served);
        } else {
            LoadingMoreFiles.LOGGER.debug("Serving {} from {}", requested, served);
        }
    }

    /** A one-line description of what the registry can do, for the startup log. */
    public static String describe() {
        List<FormatAlias> current = aliases;
        if (current.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (FormatAlias alias : current) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(alias.pathPrefix())
                    .append('*').append(alias.canonicalExtension())
                    .append(" <- ").append(alias.aliasExtension());
        }
        return builder.toString();
    }
}
