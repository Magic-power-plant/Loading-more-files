package com.mpp.loadingmorefiles.audio;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

final class WavStream extends PcmAudioStream {
    private final AudioInputStream pcm;
    private final byte[] block = new byte[8192];

    static WavStream open(InputStream input) throws IOException {
        try {
            AudioInputStream wav = AudioSystem.getAudioInputStream(new BufferedInputStream(input));
            AudioFormat source = wav.getFormat();
            AudioFormat target = pcmFormat(source.getSampleRate(), source.getChannels());
            return new WavStream(AudioSystem.getAudioInputStream(target, wav));
        } catch (UnsupportedAudioFileException | IllegalArgumentException exception) {
            throw new IOException("Unsupported WAV encoding", exception);
        }
    }

    private WavStream(AudioInputStream pcm) {
        super(pcm, pcm.getFormat());
        this.pcm = pcm;
    }

    @Override
    protected boolean writeBlock(ByteArrayOutputStream output) throws IOException {
        int length = pcm.read(block);
        if (length < 0) {
            return false;
        }
        if (length == 0) {
            throw new IOException("WAV decoder returned an incomplete sample frame");
        }
        output.write(block, 0, length);
        return true;
    }
}
