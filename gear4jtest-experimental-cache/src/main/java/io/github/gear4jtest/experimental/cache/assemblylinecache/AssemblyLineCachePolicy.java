package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Duration;
import java.util.Objects;

public record AssemblyLineCachePolicy(boolean enabled,
                                      NoDependencyCachePolicy noDependencyCachePolicy,
                                      Duration defaultTtl) {
    public AssemblyLineCachePolicy {
        Objects.requireNonNull(noDependencyCachePolicy, "noDependencyCachePolicy");
        if (noDependencyCachePolicy == NoDependencyCachePolicy.USE_DEFAULT_TTL) {
            Objects.requireNonNull(defaultTtl, "defaultTtl must not be null when USE_DEFAULT_TTL is used");
            if (defaultTtl.isNegative() || defaultTtl.isZero()) {
                throw new IllegalArgumentException("defaultTtl must be > 0");
            }
        }
    }
}
