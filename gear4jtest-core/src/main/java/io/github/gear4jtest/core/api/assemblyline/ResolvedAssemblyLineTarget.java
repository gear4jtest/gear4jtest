package io.github.gear4jtest.core.api.assemblyline;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Runtime target produced after a declarative reference has been resolved by a
 * compiler/loader.
 */
public record ResolvedAssemblyLineTarget<IN, OUT>(AssemblyLineReference declaredReference,
                                                  AssemblyLineReference resolvedReference,
                                                  AssemblyLine<IN, OUT> pipeline)
        implements AssemblyLineTarget<IN, OUT> {
    public ResolvedAssemblyLineTarget {
        Objects.requireNonNull(declaredReference, "declaredReference must not be null");
        Objects.requireNonNull(resolvedReference, "resolvedReference must not be null");
        Objects.requireNonNull(pipeline, "pipeline must not be null");
    }

    @Override
    public Optional<AssemblyLineReference> getResolvedReference() {
        return Optional.of(resolvedReference);
    }

    @Override
    public Optional<AssemblyLine<IN, OUT>> getResolvedAssemblyLine() {
        return Optional.of(pipeline);
    }
}
