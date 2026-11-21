package com.mpp.loadingmorefiles.format;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

public abstract class AtlasResource extends Resource {
    protected AtlasResource(PackResources pack, IoSupplier<InputStream> image, IoSupplier<ResourceMetadata> metadata) {
        super(pack, image, metadata);
    }

    public abstract Resource forAtlas() throws IOException;
}
