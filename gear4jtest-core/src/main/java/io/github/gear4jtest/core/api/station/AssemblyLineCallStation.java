package io.github.gear4jtest.core.api.station;

import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineExecutionMode;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineTarget;
import io.github.gear4jtest.core.api.assemblyline.DirectAssemblyLineTarget;
import io.github.gear4jtest.core.api.assemblyline.ResolvedAssemblyLineTarget;

/**
 * Station that calls another pipeline, either inline inside the current run or
 * as a real nested run.
 */
public final class AssemblyLineCallStation<IN, OUT> extends AbstractStation<IN, OUT> {
    private final AssemblyLineTarget<IN, OUT> target;
    private final AssemblyLineExecutionMode executionMode;

    private AssemblyLineCallStation(String id,
                                    AssemblyLineTarget<IN, OUT> target,
                                    AssemblyLineExecutionMode executionMode) {
        super(id, StationKind.ASSEMBLY_LINE, null, null, null, false, null, null);
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode must not be null");
    }

    public static <IN, OUT> Builder<IN, OUT> builder(String id) {
        return new Builder<>(id);
    }

    public static <IN, OUT> AssemblyLineCallStation<IN, OUT> inline(String id,
                                                                    AssemblyLine<IN, OUT> childAssemblyLine) {
        return AssemblyLineCallStation.<IN, OUT>builder(id).inline().directTarget(childAssemblyLine).build();
    }

    public static <IN, OUT> AssemblyLineCallStation<IN, OUT> nestedRun(String id,
                                                                       AssemblyLine<IN, OUT> childAssemblyLine) {
        return AssemblyLineCallStation.<IN, OUT>builder(id).nestedRun().directTarget(childAssemblyLine).build();
    }

    public AssemblyLineTarget<IN, OUT> getTarget() {
        return target;
    }

    public AssemblyLineExecutionMode getExecutionMode() {
        return executionMode;
    }

    public static final class Builder<IN, OUT> {
        private final String id;
        private AssemblyLineTarget<IN, OUT> target;
        private AssemblyLineExecutionMode executionMode = AssemblyLineExecutionMode.NESTED_RUN;

        private Builder(String id) {
            this.id = id;
        }

        public Builder<IN, OUT> target(AssemblyLineTarget<IN, OUT> target) {
            this.target = target;
            return this;
        }

        public Builder<IN, OUT> directTarget(AssemblyLine<IN, OUT> childAssemblyLine) {
            this.target = new DirectAssemblyLineTarget<>(childAssemblyLine);
            return this;
        }

        public Builder<IN, OUT> resolvedTarget(ResolvedAssemblyLineTarget<IN, OUT> target) {
            this.target = target;
            return this;
        }

        public Builder<IN, OUT> executionMode(AssemblyLineExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public Builder<IN, OUT> inline() {
            this.executionMode = AssemblyLineExecutionMode.INLINE;
            return this;
        }

        public Builder<IN, OUT> nestedRun() {
            this.executionMode = AssemblyLineExecutionMode.NESTED_RUN;
            return this;
        }

        public AssemblyLineCallStation<IN, OUT> build() {
            return new AssemblyLineCallStation<>(id, target, executionMode);
        }
    }
}
