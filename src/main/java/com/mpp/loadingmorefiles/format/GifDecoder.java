package com.mpp.loadingmorefiles.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

final class GifDecoder implements ResourceDecoder {
    @Override
    public InputStream decode(InputStream stream) throws IOException {
        return PngStream.encode(GifFrames.read(stream, false).sheet());
    }

    @Override
    public Resource createResource(PackResources pack, IoSupplier<InputStream> source, IoSupplier<InputStream> image,
                                   IoSupplier<ResourceMetadata> metadata) {
        return new AtlasResource(pack, image, metadata) {
            @Override
            public Resource forAtlas() throws IOException {
                GifFrames frames = GifFrames.read(source.get(), true);
                return new Resource(pack, () -> PngStream.encode(frames.sheet()), frames.ticks().length == 1
                        ? metadata : () -> withAnimation(metadata.get(), frames));
            }
        };
    }

    private static ResourceMetadata withAnimation(ResourceMetadata sidecar, GifFrames frames) {
        JsonArray sequence = new JsonArray();
        for (int index = 0; index < frames.ticks().length; index++) {
            JsonObject frame = new JsonObject();
            frame.addProperty("index", index);
            frame.addProperty("time", frames.ticks()[index]);
            sequence.add(frame);
        }
        JsonObject animation = new JsonObject();
        animation.addProperty("width", frames.sheet().getWidth());
        animation.addProperty("height", frames.frameHeight());
        animation.add("frames", sequence);
        return new ResourceMetadata() {
            @Override
            public <T> Optional<T> getSection(MetadataSectionSerializer<T> serializer) {
                Optional<T> existing = sidecar.getSection(serializer);
                if (existing.isEmpty() && serializer.getMetadataSectionName().equals("animation")) {
                    return Optional.of(serializer.fromJson(animation));
                }
                return existing;
            }
        };
    }
}
