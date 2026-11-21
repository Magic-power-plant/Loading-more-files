package com.mpp.loadingmorefiles.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

final class Mp3Stream extends PcmAudioStream {
    private final Bitstream frames;
    private final Decoder decoder = new Decoder();
    private final ByteBuffer block = ByteBuffer.allocate(SampleBuffer.OBUFFERSIZE * Short.BYTES).order(ByteOrder.nativeOrder());
    private Header firstFrame;

    static Mp3Stream open(InputStream input) throws IOException {
        Bitstream frames = new Bitstream(input);
        try {
            Header first = frames.readFrame();
            if (first == null || first.layer() != 3) {
                throw new IOException("Missing MP3 frame");
            }
            return new Mp3Stream(input, pcmFormat(first.frequency(), first.mode() == Header.SINGLE_CHANNEL ? 1 : 2), frames, first);
        } catch (JavaLayerException exception) {
            throw new IOException("Invalid MP3 header", exception);
        }
    }

    private Mp3Stream(InputStream input, AudioFormat format, Bitstream frames, Header firstFrame) {
        super(input, format);
        this.frames = frames;
        this.firstFrame = firstFrame;
    }

    @Override
    protected boolean writeBlock(ByteArrayOutputStream output) throws IOException {
        try {
            Header frame = firstFrame != null ? firstFrame : frames.readFrame();
            firstFrame = null;
            if (frame == null) {
                return false;
            }
            SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(frame, frames);
            block.asShortBuffer().put(samples.getBuffer(), 0, samples.getBufferLength());
            output.write(block.array(), 0, samples.getBufferLength() * Short.BYTES);
            return true;
        } catch (JavaLayerException exception) {
            throw new IOException("Invalid MP3 frame", exception);
        } finally {
            frames.closeFrame();
        }
    }
}
