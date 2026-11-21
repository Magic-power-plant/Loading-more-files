package com.mpp.loadingmorefiles.format;

import java.util.Objects;

/**
 * One alternative file name that can stand in for a name the vanilla game asks for.
 *
 * <p>An alias describes a single <em>source</em> extension (for example {@code .jpg}) and the
 * <em>canonical</em> extension it is able to satisfy (for example {@code .png}). Because the
 * vanilla consumers of a resource are only aware of the canonical name, an aliased file is
 * addressed by its canonical name everywhere above the pack layer: the sprite id, the texture
 * id and the atlas entry all stay exactly what a {@code .png} file would have produced.</p>
 *
 * <p>{@code pathPrefix} narrows the alias to one kind of resource. Texture aliases only apply
 * below {@code textures/}, so a data pack that happens to contain a {@code .jpg} file is never
 * reinterpreted.</p>
 *
 * @param formatId          stable identifier of the format family, for diagnostics
 * @param canonicalExtension extension requested by the game, including the dot
 * @param aliasExtension    alternative extension found in packs, including the dot
 * @param pathPrefix        resource path prefix the alias applies to, empty for "any path"
 */
public record FormatAlias(String formatId, String canonicalExtension, String aliasExtension, String pathPrefix,
                          ResourceDecoder decoder) {

    public FormatAlias(String formatId, String canonicalExtension, String aliasExtension, String pathPrefix) {
        this(formatId, canonicalExtension, aliasExtension, pathPrefix, ResourceDecoder.IDENTITY);
    }

    public FormatAlias {
        Objects.requireNonNull(formatId, "formatId");
        Objects.requireNonNull(decoder, "decoder");
        canonicalExtension = requireExtension(canonicalExtension, "canonicalExtension");
        aliasExtension = requireExtension(aliasExtension, "aliasExtension");
        Objects.requireNonNull(pathPrefix, "pathPrefix");
        if (!pathPrefix.isEmpty() && !pathPrefix.endsWith("/")) {
            throw new IllegalArgumentException("pathPrefix must be empty or end with '/': " + pathPrefix);
        }
    }

    private static String requireExtension(String extension, String name) {
        Objects.requireNonNull(extension, name);
        if (extension.length() < 2 || extension.charAt(0) != '.' || extension.indexOf('/') >= 0) {
            throw new IllegalArgumentException(name + " must be a file extension such as \".png\": " + extension);
        }
        return extension;
    }

    /** Whether {@code path} is a request this alias can satisfy. */
    public boolean accepts(String path) {
        return path.startsWith(this.pathPrefix) && path.endsWith(this.canonicalExtension);
    }

    /** Whether {@code path} is a file this alias would answer for. */
    public boolean isAliasPath(String path) {
        return path.startsWith(this.pathPrefix) && path.endsWith(this.aliasExtension);
    }

    /** Turns a requested canonical path into the alternative path that may replace it. */
    public String toAliasPath(String canonicalPath) {
        return canonicalPath.substring(0, canonicalPath.length() - this.canonicalExtension.length())
                + this.aliasExtension;
    }

    /** Turns an alternative file path into the canonical name it is served as. */
    public String toCanonicalPath(String aliasPath) {
        return aliasPath.substring(0, aliasPath.length() - this.aliasExtension.length())
                + this.canonicalExtension;
    }
}
