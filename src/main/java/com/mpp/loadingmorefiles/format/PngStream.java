package com.mpp.loadingmorefiles.format;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

final class PngStream {
    private PngStream() {}

    static InputStream encode(BufferedImage image) throws IOException {
        var png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", png)) {
            throw new IOException("PNG writer unavailable");
        }
        return new ByteArrayInputStream(png.toByteArray());
    }
}
