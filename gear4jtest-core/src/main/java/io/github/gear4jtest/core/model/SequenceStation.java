package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

public class SequenceStation<IN, OUT> extends AbstractStation<IN, OUT> {

    private final List<AbstractStation<?, ?>> steps;

    private SequenceStation(String id, String name, List<AbstractStation<?, ?>> steps) {
        super(id, StationKind.OTHER);
        this.steps = List.copyOf(steps);
    }

    public List<AbstractStation<?, ?>> getSteps() {
        return steps;
    }

    public static class Builder<IN, OUT> {

        private final String id;
        private final String name;
        private final List<AbstractStation<?, ?>> accumulatedSteps;

        public static <I> Builder<I, I> create(String id) {
            return new Builder<>(id, id, new ArrayList<>());
        }

        private Builder(String id, String name, List<AbstractStation<?, ?>> steps) {
            this.id = id;
            this.name = name;
            this.accumulatedSteps = steps;
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
