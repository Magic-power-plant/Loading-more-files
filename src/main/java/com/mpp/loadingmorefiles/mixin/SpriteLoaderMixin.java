package com.mpp.loadingmorefiles.mixin;

import com.mpp.loadingmorefiles.format.AtlasResource;
import java.io.IOException;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SpriteLoader.class)
public abstract class SpriteLoaderMixin {
    @ModifyVariable(method = "loadSprite", at = @At("HEAD"), argsOnly = true)
    private static Resource loadingmorefiles$atlasImage(Resource resource) {
        if (resource instanceof AtlasResource atlas) {
            try {
                return atlas.forAtlas();
            } catch (IOException | RuntimeException exception) {
                return new Resource(resource.source(), () -> { throw new IOException("Could not decode atlas texture", exception); });
            }
        }
        return resource;
    }
}
