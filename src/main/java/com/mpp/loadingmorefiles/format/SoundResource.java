package com.mpp.loadingmorefiles.format;

import java.io.InputStream;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

public final class SoundResource extends Resource {
    public enum Encoding implements ResourceDecoder {
        WAV, MP3, FLAC;

        @Override
        public InputStream decode(InputStream stream) {
            return stream;
        }

        @Override
        public Resource createResource(PackResources pack, IoSupplier<InputStream> source,
                                       IoSupplier<InputStream> decoded, IoSupplier<ResourceMetadata> metadata) {
            return new SoundResource(this, pack, source, metadata);
        }
    }

    private final Encoding encoding;

    private SoundResource(Encoding encoding, PackResources pack, IoSupplier<InputStream> source,
                          IoSupplier<ResourceMetadata> metadata) {
        super(pack, source, metadata);
        this.encoding = encoding;
    }

    public Encoding encoding() {
        return encoding;
    }
}
