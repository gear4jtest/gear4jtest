package io.github.gear4jtest.core.api.assemblyline;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Declarative target used before a compiler/loader resolves the referenced
 * pipeline.
 */
public record ReferencedAssemblyLineTarget<IN, OUT>(AssemblyLineReference reference)
        implements AssemblyLineTarget<IN, OUT> {
    public ReferencedAssemblyLineTarget {
        Objects.requireNonNull(reference, "reference must not be null");
    }

    @Override
    public AssemblyLineReference declaredReference() {
        return reference;
    }

    @Override
    public Optional<AssemblyLineReference> getResolvedReference() {
        return Optional.empty();
    }

    @Override
    public Optional<AssemblyLine<IN, OUT>> getResolvedAssemblyLine() {
        return Optional.empty();
    }
}
