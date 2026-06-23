package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class SequenceStation<IN, OUT> extends AbstractStation<IN, OUT> {
    private final List<AbstractStation<?, ?>> steps;
    private final FlowConfig flowConfig;
    private final boolean synthetic;

    private SequenceStation(String id, List<AbstractStation<?, ?>> steps, FlowConfig flowConfig, boolean synthetic) {
        super(id, StationKind.SEQUENCE, null, null, null, false, null, null);
        this.steps = steps == null || steps.isEmpty() ? List.of() : List.copyOf(steps);
        this.flowConfig = flowConfig;
        this.synthetic = synthetic;
    }

    /**
     * Creates a synthetic root sequence from already collected steps.
     *
     * <p>
     * This is used by {@link AssemblyLine}: users append stations through the
     * builder while the engine executes one root station.
     * </p>
     */
    public static SequenceStation<Object, Object> syntheticRoot(String id,
                                                                List<AbstractStation<?, ?>> steps,
                                                                FlowConfig flowConfig) {
        return new SequenceStation<>(id, steps, flowConfig, true);
    }

    public List<AbstractStation<?, ?>> getSteps() {
        return steps;
    }

    public FlowConfig getFlowConfig() {
        return flowConfig;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public static class Builder<IN, OUT> {
        private final String id;
        private final List<AbstractStation<?, ?>> accumulatedSteps;
        private FlowConfig flowConfig;

        private Builder(String id, List<AbstractStation<?, ?>> steps) {
            this.id = id;
            this.accumulatedSteps = steps;
        }

        public static <I> Builder<I, I> create(String id) {
            return new Builder<>(id, new ArrayList<>());
        }

        public <NEXT_OUT> Builder<IN, NEXT_OUT> next(AbstractStation<OUT, NEXT_OUT> nextStep) {
            this.accumulatedSteps.add(nextStep);

            Builder<IN, NEXT_OUT> next = new Builder<>(this.id, this.accumulatedSteps);
            next.flowConfig = this.flowConfig;
            return next;
        }

        public Builder<IN, OUT> flowConfig(FlowConfig flowConfig) {
            this.flowConfig = flowConfig;
            return this;
        }

        public SequenceStation<IN, OUT> build() {
            return new SequenceStation<>(id, accumulatedSteps, flowConfig, false);
        }
    }
}
