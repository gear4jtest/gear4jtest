package io.github.gear4jtest.core.api.station;

import java.util.Objects;

/**
 * Typed handle for a named container branch output.
 *
 * <p>
 * The handle lets container aggregators read branch results by explicit name
 * instead of relying on the positional {@code Object...} contract. The optional
 * Java type is used as a runtime guard when values are read from
 * {@link ContainerResults}.
 */
public final class BranchOutput<T> {
    private final String id;
    private final Class<T> type;

    private BranchOutput(String id, Class<T> type) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("branch output id is required");
        }
        this.id = id;
        this.type = type;
    }

    public static <T> BranchOutput<T> of(String id, Class<T> type) {
        return new BranchOutput<>(id, Objects.requireNonNull(type, "branch output type is required"));
    }

    public static <T> BranchOutput<T> untyped(String id) {
        return new BranchOutput<>(id, null);
    }

    public String id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    boolean hasRuntimeType() {
        return type != null;
    }

    @Override
    public String toString() {
        return type == null ? id : id + "<" + type.getSimpleName() + ">";
    }
}
