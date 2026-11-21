package com.mpp.loadingmorefiles.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;

public abstract class PcmAudioStream implements AudioStream {
    private final InputStream source;
    private final AudioFormat format;
    private boolean ended;

    protected PcmAudioStream(InputStream source, AudioFormat format) {
        this.source = source;
        this.format = format;
    }

    static AudioFormat pcmFormat(float sampleRate, int channels) throws IOException {
        if ((channels != 1 && channels != 2) || !(sampleRate > 0)) {
            throw new IOException("Unsupported audio format: " + channels + " channels at " + sampleRate + " Hz");
        }
        return new AudioFormat(sampleRate, 16, channels, true, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
    }

    @Override
    public final AudioFormat getFormat() {
        return format;
    }

    protected abstract boolean writeBlock(ByteArrayOutputStream output) throws IOException;

    @Override
    public final ByteBuffer read(int size) throws IOException {
        PcmBuffer pcm = new PcmBuffer();
        while (!ended && pcm.size() < size) {
            ended = !writeBlock(pcm);
        }
        return pcm.toDirectBuffer();
    }

    public final ByteBuffer readAll() throws IOException {
        return read(Integer.MAX_VALUE);
    }

    @Override
    public final void close() throws IOException {
        ended = true;
        source.close();
    }

    private static final class PcmBuffer extends ByteArrayOutputStream {
        ByteBuffer toDirectBuffer() {
            return ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder()).put(buf, 0, count).flip();
        }
    }
}
