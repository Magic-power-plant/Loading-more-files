package com.mpp.loadingmorefiles.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import org.jflac.ChannelData;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.io.BitInputStream;
import org.jflac.metadata.StreamInfo;

final class FlacStream extends PcmAudioStream {
    private final FLACDecoder decoder;
    private final StreamInfo info;
    private long samplesRead;

    static FlacStream open(InputStream input) throws IOException {
        FLACDecoder decoder = new FLACDecoder(input);
        StreamInfo info = decoder.readStreamInfo();
        AudioFormat format = pcmFormat(info.getSampleRate(), info.getChannels());
        if (info.getBitsPerSample() < 4 || info.getBitsPerSample() > 24) {
            throw new IOException("Unsupported FLAC bit depth: " + info.getBitsPerSample());
        }
        BitInputStream bits = decoder.getBitInputStream();
        boolean last = info.isLast();
        while (!last) {
            last = (bits.readRawUInt(8) & 0x80) != 0;
            bits.skipBitsNoCRC(bits.readRawUInt(24) * 8);
        }
        return new FlacStream(input, format, decoder, info);
    }

    private FlacStream(InputStream input, AudioFormat format, FLACDecoder decoder, StreamInfo info) {
        super(input, format);
        this.decoder = decoder;
        this.info = info;
    }

    @Override
    protected boolean writeBlock(ByteArrayOutputStream output) throws IOException {
        Frame frame = decoder.readNextFrame();
        if (frame == null) {
            if (info.getTotalSamples() > 0 && samplesRead != info.getTotalSamples()) {
                throw new IOException("Incomplete FLAC stream");
            }
            return false;
        }
        if (frame.header.channels != info.getChannels() || frame.header.bitsPerSample != info.getBitsPerSample()
                || frame.header.sampleRate != info.getSampleRate()) {
            throw new IOException("FLAC frame does not match STREAMINFO");
        }
        ByteBuffer block = ByteBuffer.allocate(frame.header.blockSize * getFormat().getFrameSize()).order(ByteOrder.nativeOrder());
        ChannelData[] channels = decoder.getChannelData();
        for (int sample = 0; sample < frame.header.blockSize; sample++) {
            for (int channel = 0; channel < info.getChannels(); channel++) {
                int value = channels[channel].getOutput()[sample];
                int depth = info.getBitsPerSample();
                block.putShort((short) (depth >= 16 ? value >> (depth - 16) : value << (16 - depth)));
            }
        }
        samplesRead += frame.header.blockSize;
        output.writeBytes(block.array());
        return true;
    }
}
