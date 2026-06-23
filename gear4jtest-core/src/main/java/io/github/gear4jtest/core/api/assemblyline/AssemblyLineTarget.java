package io.github.gear4jtest.core.api.assemblyline;

import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Target of a pipeline-call station.
 *
 * <p>
 * Declarative models may initially carry only a
 * {@link ReferencedAssemblyLineTarget}. Runtime-ready models should use either
 * {@link DirectAssemblyLineTarget} or {@link ResolvedAssemblyLineTarget} so
 * execution does not perform loading/resolution work.
 * </p>
 */
public sealed interface AssemblyLineTarget<IN, OUT>
        permits DirectAssemblyLineTarget, ReferencedAssemblyLineTarget, ResolvedAssemblyLineTarget {
    AssemblyLineReference declaredReference();

    Optional<AssemblyLineReference> getResolvedReference();

    Optional<AssemblyLine<IN, OUT>> getResolvedAssemblyLine();
}
