package io.github.gear4jtest.core.api.station;

import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.pipeline.DirectPipelineTarget;
import io.github.gear4jtest.core.api.pipeline.PipelineExecutionMode;
import io.github.gear4jtest.core.api.pipeline.PipelineTarget;
import io.github.gear4jtest.core.api.pipeline.ResolvedPipelineTarget;

/**
 * Station that calls another pipeline, either inline inside the current run or
 * as a real nested run.
 */
public final class PipelineCallStation<IN, OUT> extends AbstractStation<IN, OUT> {

    private final PipelineTarget<IN, OUT> target;
    private final PipelineExecutionMode executionMode;

    private PipelineCallStation(String id, PipelineTarget<IN, OUT> target, PipelineExecutionMode executionMode) {
        super(id, StationKind.PIPELINE);
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode must not be null");
    }

    public static <IN, OUT> Builder<IN, OUT> builder(String id) {
        return new Builder<>(id);
    }

    public static <IN, OUT> PipelineCallStation<IN, OUT> inline(String id, AssemblyLine<IN, OUT> childPipeline) {
        return PipelineCallStation.<IN, OUT>builder(id).inline().directTarget(childPipeline).build();
    }

    public static <IN, OUT> PipelineCallStation<IN, OUT> nestedRun(String id, AssemblyLine<IN, OUT> childPipeline) {
        return PipelineCallStation.<IN, OUT>builder(id).nestedRun().directTarget(childPipeline).build();
    }

    public PipelineTarget<IN, OUT> getTarget() {
        return target;
    }

    public PipelineExecutionMode getExecutionMode() {
        return executionMode;
    }

    public static final class Builder<IN, OUT> {
        private final String id;
        private PipelineTarget<IN, OUT> target;
        private PipelineExecutionMode executionMode = PipelineExecutionMode.NESTED_RUN;

        private Builder(String id) {
            this.id = id;
        }

        public Builder<IN, OUT> target(PipelineTarget<IN, OUT> target) {
            this.target = target;
            return this;
        }

        public Builder<IN, OUT> directTarget(AssemblyLine<IN, OUT> childPipeline) {
            this.target = new DirectPipelineTarget<>(childPipeline);
            return this;
        }

        public Builder<IN, OUT> resolvedTarget(ResolvedPipelineTarget<IN, OUT> target) {
            this.target = target;
            return this;
        }

        public Builder<IN, OUT> executionMode(PipelineExecutionMode executionMode) {
            this.executionMode = executionMode;
            return this;
        }

        public Builder<IN, OUT> inline() {
            this.executionMode = PipelineExecutionMode.INLINE;
            return this;
        }

        public Builder<IN, OUT> nestedRun() {
            this.executionMode = PipelineExecutionMode.NESTED_RUN;
            return this;
        }

        public PipelineCallStation<IN, OUT> build() {
            return new PipelineCallStation<>(id, target, executionMode);
        }
    }
}
