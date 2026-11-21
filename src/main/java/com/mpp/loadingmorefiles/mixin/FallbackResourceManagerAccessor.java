package com.mpp.loadingmorefiles.mixin;

import java.util.List;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the two private members of {@code FallbackResourceManager} the format layer
 * needs when it has to re-read a pack layer by hand: which {@link PackType} the manager belongs
 * to, and the ordered list of pack layers.
 *
 * <p>Accessors are used instead of {@code @Shadow} fields because the layer element type is
 * package-private; the returned list is still only read, never modified.</p>
 */
@Mixin(FallbackResourceManager.class)
public interface FallbackResourceManagerAccessor {

    /** The pack type (client resources or server data) this manager resolves for. */
    @Accessor("type")
    PackType loadingmorefiles$type();

    /** Pack layers, lowest priority first, so the last element wins. */
    @Accessor("fallbacks")
    List<?> loadingmorefiles$fallbacks();
}
