package io.github.gear4jtest.core.sidecompute;

import java.util.Objects;

public final class SideComputeKeys {
    private static final String PREFIX = "__sidecompute_value__:";

    private SideComputeKeys() {
    }

    public static String valueKey(String key) {
        validateUserKey(key);
        return PREFIX + key;
    }

    static void validateUserKey(String key) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Side-compute key must not be blank");
        }
        if (key.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Side-compute key must not start with reserved prefix " + PREFIX);
        }
    }
}
