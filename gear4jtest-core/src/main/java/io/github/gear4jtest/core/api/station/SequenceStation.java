package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class SequenceStation<IN, OUT> extends AbstractStation<IN, OUT> {

    private final List<AbstractStation<?, ?>> steps;
    private FlowConfig flowConfig;
    private boolean synthetic;

    private SequenceStation(String id, String name, List<AbstractStation<?, ?>> steps) {
        super(id, StationKind.OTHER);
        this.steps = List.copyOf(steps);
    }

    /**
     * Crée une sequence "synthetic root" à partir d'une liste de steps.
     *
     * <p>
     * Utile pour le builder d'{@link AssemblyLine} : l'utilisateur construit une
     * liste de stations, et l'engine exécute un unique root.
     */
    public static SequenceStation<Object, Object> syntheticRoot(String id,
                                                                List<AbstractStation<?, ?>> steps,
                                                                FlowConfig flowConfig) {
        SequenceStation<Object, Object> root = new SequenceStation<>(id, id, steps);
        root.setSynthetic(true);
        root.setFlowConfig(flowConfig);
        return root;
    }

    public List<AbstractStation<?, ?>> getSteps() {
        return steps;
    }

    public FlowConfig getFlowConfig() {
        return flowConfig;
    }

    public void setFlowConfig(FlowConfig flowConfig) {
        this.flowConfig = flowConfig;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    public void setSynthetic(boolean synthetic) {
        this.synthetic = synthetic;
    }

    public static class Builder<IN, OUT> {

        private final String id;
        private final String name;
        private final List<AbstractStation<?, ?>> accumulatedSteps;

        private Builder(String id, String name, List<AbstractStation<?, ?>> steps) {
            this.id = id;
            this.name = name;
            this.accumulatedSteps = steps;
        }

        public static <I> Builder<I, I> create(String id) {
            return new Builder<>(id, id, new ArrayList<>());
        }

        public <NEXT_OUT> Builder<IN, NEXT_OUT> next(AbstractStation<OUT, NEXT_OUT> nextStep) {
            this.accumulatedSteps.add(nextStep);

            return new Builder<>(this.id, this.name, this.accumulatedSteps);
        }

        public SequenceStation<IN, OUT> build() {
            return new SequenceStation<>(id, name, accumulatedSteps);
        }
    }
}
