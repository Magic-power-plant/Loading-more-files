package com.mpp.loadingmorefiles.format;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

final class TgaDecoder implements ResourceDecoder {
    @Override
    public InputStream decode(InputStream stream) throws IOException {
        try (var input = new BufferedInputStream(stream)) {
            input.mark(18);
            byte[] header = input.readNBytes(18);
            if (header.length != 18) {
                throw new IOException("Incomplete TGA header");
            }
            int descriptor = Byte.toUnsignedInt(header[17]);
            if ((descriptor & 0xc0) != 0) {
                throw new IOException("Interleaved TGA images are unsupported");
            }
            input.reset();
            try (NativeImage image = NativeImage.read(input)) {
                if ((descriptor & 0x10) != 0) {
                    flipHorizontally(image);
                }
                return new ByteArrayInputStream(image.asByteArray());
            }
        }
    }

    private static void flipHorizontally(NativeImage image) {
        for (int row = 0; row < image.getHeight(); row++) {
            for (int left = 0, right = image.getWidth() - 1; left < right; left++, right--) {
                int pixel = image.getPixelRGBA(left, row);
                image.setPixelRGBA(left, row, image.getPixelRGBA(right, row));
                image.setPixelRGBA(right, row, pixel);
            }
        }
    }
}
