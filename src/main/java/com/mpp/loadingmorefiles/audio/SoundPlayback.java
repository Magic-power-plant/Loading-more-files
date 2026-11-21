package com.mpp.loadingmorefiles.audio;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.mpp.loadingmorefiles.format.SoundResource;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.resources.ResourceLocation;

public final class SoundPlayback {
    private SoundPlayback() {}

    public static CompletableFuture<SoundBuffer> buffer(ResourceLocation location, SoundResource resource) {
        return CompletableFuture.supplyAsync(() -> {
            try (PcmAudioStream stream = decode(resource.encoding(), resource.open())) {
                return new SoundBuffer(stream.readAll(), stream.getFormat());
            } catch (IOException | RuntimeException exception) {
                throw new CompletionException("Could not buffer " + location + " as " + resource.encoding(), exception);
            }
        }, Util.backgroundExecutor());
    }

    public static CompletableFuture<AudioStream> stream(ResourceLocation location, SoundResource resource, boolean loop) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InputStream input = resource.open();
                if (!loop) {
                    return decode(resource.encoding(), input);
                }
                try {
                    return new LoopingAudioStream(source -> decode(resource.encoding(), source), input);
                } catch (IOException | RuntimeException exception) {
                    input.close();
                    throw exception;
                }
            } catch (IOException | RuntimeException exception) {
                throw new CompletionException("Could not stream " + location + " as " + resource.encoding(), exception);
            }
        }, Util.backgroundExecutor());
    }

    private static PcmAudioStream decode(SoundResource.Encoding encoding, InputStream input) throws IOException {
        try {
            return switch (encoding) {
                case WAV -> WavStream.open(input);
                case MP3 -> Mp3Stream.open(input);
                case FLAC -> FlacStream.open(input);
            };
        } catch (IOException | RuntimeException exception) {
            input.close();
            throw exception;
        }
    }
}
