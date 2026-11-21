package com.mpp.loadingmorefiles.format;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

@FunctionalInterface
public interface ResourceDecoder {
    ResourceDecoder IDENTITY = stream -> stream;

    InputStream decode(InputStream stream) throws IOException;

    default Resource createResource(PackResources pack, IoSupplier<InputStream> source, IoSupplier<InputStream> image,
                                    IoSupplier<ResourceMetadata> metadata) {
        return new Resource(pack, image, metadata);
    }

    default ResourceDecoder withSidecars(Function<String, IoSupplier<InputStream>> lookup) {
        return this;
    }
}
