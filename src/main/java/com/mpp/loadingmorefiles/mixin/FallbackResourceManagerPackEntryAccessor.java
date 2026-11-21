package com.mpp.loadingmorefiles.mixin;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@code FallbackResourceManager.PackEntry}, a package-private record that
 * describes one pack layer: the pack itself (absent for filter-only layers) and its optional
 * path filter.
 *
 * <p>Minecraft exposes the layer list as {@code fallbacks} but keeps the element type private,
 * so the format mixin reaches the record members through this accessor. Only reads are needed;
 * the pack list itself is never modified.</p>
 */
@Mixin(targets = "net.minecraft.server.packs.resources.FallbackResourceManager$PackEntry")
public interface FallbackResourceManagerPackEntryAccessor {

    /** The pack resources of this layer, or {@code null} for a layer that only filters paths. */
    @Accessor("resources")
    @Nullable
    PackResources loadingmorefiles$resources();

    /** The path filter of this layer, or {@code null} when it does not filter anything. */
    @Accessor("filter")
    @Nullable
    Predicate<ResourceLocation> loadingmorefiles$filter();
}
