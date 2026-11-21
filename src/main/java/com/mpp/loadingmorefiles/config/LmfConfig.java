package com.mpp.loadingmorefiles.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Config for the format layer.
 *
 * <p>Registered as a common config so that a dedicated server can also switch aliasing off:
 * the aliasing happens inside the shared resource manager, even though only the client ever
 * asks for textures.</p>
 */
public final class LmfConfig {

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_FORMAT_ALIASES;
    private static final ForgeConfigSpec.BooleanValue LOG_ALIASED_RESOURCES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                        "Options for file formats the vanilla game does not read itself.",
                        "Every option only adds a fallback: files in the format Minecraft already",
                        "expects always keep priority, and switching an option off restores",
                        "completely vanilla behaviour.")
                .push("formats");

        ENABLE_FORMAT_ALIASES = builder
                .comment(
                        "Let .jpg, .jpeg, .webp, .gif and .tga textures satisfy .png requests.",
                        "Texture aliases are limited to paths below textures/, so blocks, items,",
                        "GUI images, fonts and mod textures can all be stored as JPEG, WebP, GIF or TGA.",
                        "Let .wav, .mp3 and .flac files below sounds/ satisfy .ogg requests.",
                        "Models, atlases and sounds.json keep their vanilla resource names.")
                .define("enable_format_aliases", true);

        LOG_ALIASED_RESOURCES = builder
                .comment(
                        "Log every resource that was served from an alternative file.",
                        "Debug logging prints these lines to logs/debug.log; enabling this option",
                        "promotes them to the normal log (useful while converting a pack, noisy",
                        "for large packs).")
                .define("log_aliased_resources", false);

        builder.pop();
        SPEC = builder.build();
    }

    private LmfConfig() {}

    /**
     * Whether extension aliasing is active. Before the config is loaded the built-in default
     * applies, which keeps the aliases usable during early resource lookups.
     */
    public static boolean aliasesEnabled() {
        return !SPEC.isLoaded() || ENABLE_FORMAT_ALIASES.get();
    }

    /** Whether every aliased resource should be reported at info level. */
    public static boolean logAliasedResources() {
        return SPEC.isLoaded() && LOG_ALIASED_RESOURCES.get();
    }
}
