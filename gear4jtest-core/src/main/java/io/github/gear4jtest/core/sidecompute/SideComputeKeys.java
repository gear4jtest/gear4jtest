package io.github.gear4jtest.core.sidecompute;

public final class SideComputeKeys {
    private static final String PREFIX = "__sidecompute_value__:";

    public static String valueKey(String key) {
        return PREFIX + key;
    }
}
