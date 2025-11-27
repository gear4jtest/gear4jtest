package io.github.gear4jtest.core.sidecompute;

public interface SideComputeAccessor {

    <T> T get(String key, Class<T> type);

    boolean isPresent(String key);
}
