package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineExecutionMode;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.station.SequenceStation;

/**
 * Builders for assembly-line definitions and assembly-line call stations.
 */
public final class AssemblyLines {
    private AssemblyLines() {
    }

    public static <T> AssemblyLine.Builder<T, T> createAssemblyLine(String identifier) {
        return AssemblyLine.builder(identifier);
    }

    public static <IN, OUT> SequenceStation.Builder<IN, OUT> chain(String id, AbstractStation<IN, OUT> step) {
        return SequenceStation.Builder.<IN>create(id).next(step);
    }

    public static <IN, OUT> AssemblyLineCallStation.Builder<IN, OUT> assemblyLineCall(String id) {
        return AssemblyLineCallStation.builder(id);
    }

    public static <IN, OUT> AssemblyLineCallStation<IN, OUT> inlineAssemblyLine(String id,
                                                                                AssemblyLine<IN, OUT> childAssemblyLine) {
        return AssemblyLineCallStation.<IN, OUT>builder(id).executionMode(AssemblyLineExecutionMode.INLINE)
                .directTarget(childAssemblyLine).build();
    }

    public static <IN, OUT> AssemblyLineCallStation<IN, OUT> nestedAssemblyLine(String id,
                                                                                AssemblyLine<IN, OUT> childAssemblyLine) {
        return AssemblyLineCallStation.<IN, OUT>builder(id).executionMode(AssemblyLineExecutionMode.NESTED_RUN)
                .directTarget(childAssemblyLine).build();
    }
}
