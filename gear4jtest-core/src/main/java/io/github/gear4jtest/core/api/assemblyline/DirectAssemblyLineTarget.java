package io.github.gear4jtest.core.api.assemblyline;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Runtime target used when Java code already has the child assembly line
 * instance.
 */
public record DirectAssemblyLineTarget<IN, OUT>(AssemblyLine<IN, OUT> assemblyLine)
        implements AssemblyLineTarget<IN, OUT> {
    public DirectAssemblyLineTarget {
        Objects.requireNonNull(assemblyLine, "assemblyLine must not be null");
    }

    @Override
    public AssemblyLineReference declaredReference() {
        return AssemblyLineReference.from(assemblyLine);
    }

    @Override
    public Optional<AssemblyLineReference> getResolvedReference() {
        return Optional.of(AssemblyLineReference.from(assemblyLine));
    }

    @Override
    public Optional<AssemblyLine<IN, OUT>> getResolvedAssemblyLine() {
        return Optional.of(assemblyLine);
    }
}
