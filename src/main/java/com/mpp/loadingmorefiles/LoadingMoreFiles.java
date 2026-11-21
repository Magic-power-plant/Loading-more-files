package com.mpp.loadingmorefiles;

import com.mpp.loadingmorefiles.config.LmfConfig;
import com.mpp.loadingmorefiles.format.ResourceFormats;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loading More Files
 *
 * <p>Entry point of the mod. Its purpose is to teach Minecraft how to load
 * resource and data files in formats the vanilla game does not understand
 * (for example {@code .wav}/{@code .mp3} sounds, {@code .jpg} textures or
 * alternative data encodings), so packs and add-ons do not have to be
 * converted by hand before they can be used.</p>
 *
 * <p>The resource manager resolves JPEG and WebP textures for {@code .png} requests.
 * JPEG uses Minecraft's image reader and WebP is converted to PNG on open.</p>
 */
@Mod(LoadingMoreFiles.MODID)
public class LoadingMoreFiles {

    /** Mod id, must match {@code mod_id} in gradle.properties and mods.toml. */
    public static final String MODID = "loadingmorefiles";

    /** Shared logger, so every part of the mod reports under one name. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    // Forge marks ModLoadingContext#get as deprecated for removal in 1.21.1; on 1.20.1 it is
    // still the only way for a mod to register its own config spec.
    @SuppressWarnings("removal")
    public LoadingMoreFiles() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LmfConfig.SPEC);
        ResourceFormats.registerImageFormats();
        ResourceFormats.registerSoundFormats();
        LOGGER.info("Format aliases: {}", ResourceFormats.describe());
    }

}
