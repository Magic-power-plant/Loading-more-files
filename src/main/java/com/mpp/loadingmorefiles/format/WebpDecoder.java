package com.mpp.loadingmorefiles.format;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

public final class WebpDecoder implements ResourceDecoder {
    @Override
    public InputStream decode(InputStream stream) throws IOException {
        try (stream; var input = new MemoryCacheImageInputStream(stream)) {
            ImageReader reader = new WebPImageReaderSpi().createReaderInstance();
            try {
                reader.setInput(input);
                BufferedImage image = reader.read(0);
                return PngStream.encode(image);
            } finally {
                reader.dispose();
            }
        }
    }
}
