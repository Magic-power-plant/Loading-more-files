package com.mpp.loadingmorefiles.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.mpp.loadingmorefiles.audio.SoundPlayback;
import com.mpp.loadingmorefiles.format.SoundResource;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Shadow @Final private ResourceProvider resourceManager;
    @Shadow @Final private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void loadingmorefiles$buffer(ResourceLocation location, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> result) {
        if (resourceManager.getResource(location).orElse(null) instanceof SoundResource sound) {
            result.setReturnValue(cache.computeIfAbsent(location, key -> SoundPlayback.buffer(key, sound)));
        }
    }

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void loadingmorefiles$stream(ResourceLocation location, boolean loop, CallbackInfoReturnable<CompletableFuture<AudioStream>> result) {
        if (resourceManager.getResource(location).orElse(null) instanceof SoundResource sound) {
            result.setReturnValue(SoundPlayback.stream(location, sound, loop));
        }
    }
}
