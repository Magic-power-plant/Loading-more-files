package com.mpp.loadingmorefiles.format;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Element;

record GifFrames(BufferedImage sheet, int frameHeight, int[] ticks) {
    private static final int MAX_PIXELS = 16 * 1024 * 1024;

    static GifFrames read(InputStream stream, boolean allFrames) throws IOException {
        try (stream; var input = new MemoryCacheImageInputStream(stream)) {
            var readers = ImageIO.getImageReadersByFormatName("GIF");
            if (!readers.hasNext()) {
                throw new IOException("GIF reader unavailable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return composite(reader, allFrames ? reader.getNumImages(true) : 1);
            } finally {
                reader.dispose();
            }
        }
    }

    private static GifFrames composite(ImageReader reader, int count) throws IOException {
        Element metadata = root(reader.getStreamMetadata());
        Element screen = child(metadata, "LogicalScreenDescriptor");
        int width = number(screen, "logicalScreenWidth");
        int height = number(screen, "logicalScreenHeight");
        if (count < 1 || width < 1 || height < 1 || (long) width * height * count > MAX_PIXELS) {
            throw new IOException("Unsupported GIF sheet dimensions: " + width + "x" + height + " x " + count);
        }

        BufferedImage sheet = new BufferedImage(width, height * count, BufferedImage.TYPE_INT_ARGB);
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D state = canvas.createGraphics();
        Graphics2D output = sheet.createGraphics();
        int[] ticks = new int[count];
        Color background = background(metadata);
        try {
            for (int index = 0; index < count; index++) {
                Element frame = root(reader.getImageMetadata(index));
                Element descriptor = child(frame, "ImageDescriptor");
                Element control = child(frame, "GraphicControlExtension");
                int left = number(descriptor, "imageLeftPosition");
                int top = number(descriptor, "imageTopPosition");
                int frameWidth = number(descriptor, "imageWidth");
                int frameHeight = number(descriptor, "imageHeight");
                if (frameWidth < 1 || frameHeight < 1 || left + frameWidth > width || top + frameHeight > height) {
                    throw new IOException("GIF frame " + index + " exceeds the logical screen");
                }
                boolean transparent = control != null && Boolean.parseBoolean(control.getAttribute("transparentColorFlag"));
                state.setBackground(transparent ? new Color(0, true) : background);
                if (index == 0) {
                    state.clearRect(0, 0, width, height);
                }
                BufferedImage patch = reader.read(index);
                int offset = index * height;
                output.drawImage(canvas, 0, offset, null);
                output.drawImage(patch, left, offset + top, null);
                ticks[index] = control == null ? 1 : Math.max(1, (number(control, "delayTime") + 2) / 5);
                if (index + 1 < count) {
                    String disposal = control == null ? "none" : control.getAttribute("disposalMethod");
                    if (disposal.equals("restoreToBackgroundColor")) {
                        state.clearRect(left, top, frameWidth, frameHeight);
                    } else if (!disposal.equals("restoreToPrevious")) {
                        state.drawImage(patch, left, top, null);
                    }
                }
            }
        } finally {
            output.dispose();
            state.dispose();
        }
        return new GifFrames(sheet, height, ticks);
    }

    private static Color background(Element metadata) {
        Element table = child(metadata, "GlobalColorTable");
        if (table != null) {
            int index = number(table, "backgroundColorIndex");
            var entries = table.getElementsByTagName("ColorTableEntry");
            for (int position = 0; position < entries.getLength(); position++) {
                Element entry = (Element) entries.item(position);
                if (number(entry, "index") == index) {
                    return new Color(number(entry, "red"), number(entry, "green"), number(entry, "blue"));
                }
            }
        }
        return new Color(0, true);
    }

    private static Element root(IIOMetadata metadata) {
        return (Element) metadata.getAsTree(metadata.getNativeMetadataFormatName());
    }

    private static Element child(Element parent, String name) {
        return (Element) parent.getElementsByTagName(name).item(0);
    }

    private static int number(Element element, String attribute) {
        return Integer.parseInt(element.getAttribute(attribute));
    }
}
