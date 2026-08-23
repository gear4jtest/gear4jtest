package io.github.gear4jtest.external.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable identifier of an artifact-store implementation.
 *
 * <p>
 * Store types are deliberately open so third-party
 * {@link io.github.gear4jtest.external.api.spi.ArtifactStorePlugin plugins} can
 * be represented by
 * {@link io.github.gear4jtest.external.api.model.OperationChainConfig} without
 * a Gear4J release. Constants cover the built-in and historically advertised
 * store types.
 * </p>
 */
public record StoreType(String value) {
    private static final Pattern VALID_VALUE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");

    public static final StoreType DATABASE = new StoreType("DATABASE");
    public static final StoreType FILESYSTEM = new StoreType("FILESYSTEM");
    public static final StoreType S3 = new StoreType("S3");
    public static final StoreType SFTP = new StoreType("SFTP");
    public static final StoreType MEMORY = new StoreType("MEMORY");

    public StoreType {
        value = Objects.requireNonNull(value, "value must not be null").trim().toUpperCase(Locale.ROOT);
        if (!VALID_VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid artifact store type '" + value
                    + "'. Expected [A-Z][A-Z0-9_-]{0,63}.");
        }
    }

    public static StoreType of(String value) {
        return new StoreType(value);
    }

    /**
     * Compatibility alias for code that previously parsed the closed enum.
     */
    public static StoreType valueOf(String value) {
        return of(value);
    }

    /**
     * Compatibility alias for code that previously serialized the closed enum.
     */
    public String name() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
