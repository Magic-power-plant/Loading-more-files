package com.mpp.loadingmorefiles.format;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import javax.imageio.ImageIO;
import net.minecraft.server.packs.resources.IoSupplier;

final class JpegAlphaDecoder implements ResourceDecoder {
    @Override
    public InputStream decode(InputStream stream) {
        return stream;
    }

    @Override
    public ResourceDecoder withSidecars(Function<String, IoSupplier<InputStream>> lookup) {
        IoSupplier<InputStream> mask = lookup.apply(".alpha");
        return mask == null ? IDENTITY : stream -> {
            try (stream; InputStream maskStream = mask.get()) {
                return combine(stream, maskStream);
            }
        };
    }

    private static InputStream combine(InputStream source, InputStream maskSource) throws IOException {
        BufferedImage color = ImageIO.read(source);
        BufferedImage mask = ImageIO.read(maskSource);
        if (color == null || mask == null) {
            throw new IOException("Could not read JPEG texture or its .alpha mask");
        }
        int width = color.getWidth();
        int height = color.getHeight();
        if (width != mask.getWidth() || height != mask.getHeight()) {
            throw new IOException("Alpha mask must match JPEG dimensions " + width + "x" + height
                    + ", got " + mask.getWidth() + "x" + mask.getHeight());
        }

        Raster samples = mask.getRaster();
        boolean grayscale = mask.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;
        int maxSample = (1 << samples.getSampleModel().getSampleSize(0)) - 1;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width];
        for (int row = 0; row < height; row++) {
            color.getRGB(0, row, width, 1, pixels, 0, width);
            for (int column = 0; column < width; column++) {
                int alpha;
                if (grayscale) {
                    alpha = (samples.getSample(column, row, 0) * 255 + maxSample / 2) / maxSample;
                } else {
                    int rgb = mask.getRGB(column, row);
                    alpha = (77 * (rgb >> 16 & 255) + 150 * (rgb >> 8 & 255) + 29 * (rgb & 255) + 128) >> 8;
                }
                pixels[column] = (alpha << 24) | (pixels[column] & 0x00ffffff);
            }
            result.setRGB(0, row, width, 1, pixels, 0, width);
        }
        return PngStream.encode(result);
    }
}
