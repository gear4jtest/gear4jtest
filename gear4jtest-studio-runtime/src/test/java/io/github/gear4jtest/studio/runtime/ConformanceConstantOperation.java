package io.github.gear4jtest.studio.runtime;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

/** Test-only pure operator; never shipped in the runtime artifact. */
public final class ConformanceConstantOperation implements Operator<String, String> {
    @Override
    public String transform(String input, StationExecutionContext context) {
        return "changed";
    }
}
