package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.BranchCondition;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class ContainerBaseStation<IN, OUT> extends AbstractStation<IN, OUT> {
    protected final List<Branch<IN>> pipelines;
    protected ContainerFunction<OUT> func;
    protected boolean isParallel = false;
    protected ExecutorService executorService;
    protected FlowConfig flowConfig;
    protected Duration awaitTimeout;

    public ContainerBaseStation(List<Branch<IN>> pipelines, ContainerFunction<OUT> func) {
        super("", StationKind.CONTAINER);
        this.pipelines = pipelines;
        this.func = func;
    }

    protected static void validateUniqueBranchIds(List<? extends Branch<?>> branches) {
        Set<String> ids = new HashSet<>();

        for (Branch<?> branch : branches) {
            if (branch == null) {
                throw new IllegalArgumentException("Container contains a null branch");
            }

            String branchId = branch.getEffectiveId();
            if (branchId == null || branchId.isBlank()) {
                throw new IllegalArgumentException("Container contains a branch without explicit id");
            }

            if (!ids.add(branchId)) {
                throw new IllegalArgumentException("Container contains duplicated branch id '" + branchId + "'");
            }
        }
    }

    public List<Branch<IN>> getPipelines() {
        return Collections.unmodifiableList(pipelines);
    }

    public ContainerFunction<OUT> getFunc() {
        return func;
    }

    public boolean isParallel() {
        return isParallel;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public FlowConfig getFlowConfig() {
        return flowConfig;
    }

    public void setFlowConfig(FlowConfig flowConfig) {
        this.flowConfig = flowConfig;
    }

    public Duration getAwaitTimeout() {
        return awaitTimeout;
    }

    public void setAwaitTimeout(Duration awaitTimeout) {
        this.awaitTimeout = awaitTimeout;
    }

    @FunctionalInterface
    public interface ContainerFunction<OUT> {
        OUT apply(Object... objects);
    }

    public static class Builder<IN, OUT> {
        final List<Branch<IN>> branches = new ArrayList<>();
        boolean isParallel;
        ExecutorService executorService;
        FlowConfig flowConfig;
        Duration awaitTimeout;

        public Builder() {
        }

        public Builder(ExecutorService executorService) {
            this.isParallel = true;
            this.executorService = executorService;
        }

        public Builder<IN, OUT> flowConfig(FlowConfig flowConfig) {
            this.flowConfig = flowConfig;
            return this;
        }

        public Builder<IN, OUT> awaitTimeout(Duration awaitTimeout) {
            this.awaitTimeout = awaitTimeout;
            return this;
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement) {
            var branch = new Branch.Builder<IN>().withId(id).withOperation(startingElement).build();
            return new Container1Station.Builder<>(this, branch);
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement,
                                                                     Condition<IN> condition) {
            var branch = new Branch.Builder<IN>().withId(id).withCondition(condition).withOperation(startingElement)
                    .build();
            return new Container1Station.Builder<>(this, branch);
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement,
                                                                     BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>().withId(id).withSiblingCondition(siblingCondition)
                    .withOperation(startingElement).build();
            return new Container1Station.Builder<>(this, branch);
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement,
                                                                     Condition<IN> condition,
                                                                     BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>().withId(id).withCondition(condition)
                    .withSiblingCondition(siblingCondition).withOperation(startingElement).build();
            return new Container1Station.Builder<>(this, branch);
        }
    }

    static <IN, OUT> ContainerBaseStation<IN, OUT> buildStation(List<Branch<IN>> branches,
                                                                boolean isParallel,
                                                                ExecutorService executorService,
                                                                FlowConfig flowConfig,
                                                                Duration awaitTimeout,
                                                                ContainerFunction<OUT> function) {
        validateUniqueBranchIds(branches);
        ContainerBaseStation<IN, OUT> station = new ContainerBaseStation<>(new ArrayList<>(branches), function);
        station.executorService = executorService;
        station.isParallel = isParallel;
        station.setFlowConfig(flowConfig);
        station.setAwaitTimeout(awaitTimeout);
        return station;
    }

    public static class Branch<I> {
        private String id;
        private AbstractStation<I, ?> station;
        private Condition<I> condition;
        private BranchCondition<I> siblingCondition;

        public Branch() {
            // Empty constructor kept for fluent builder-style initialization.
        }

        public String getId() {
            return id;
        }

        public String getEffectiveId() {
            return id;
        }

        public AbstractStation<I, ?> getStation() {
            return station;
        }

        public Condition<I> getCondition() {
            return condition;
        }

        public BranchCondition<I> getSiblingCondition() {
            return siblingCondition;
        }

        public static class Builder<I> {
            private String id;
            private AbstractStation<I, ?> station;
            private Condition<I> condition;
            private BranchCondition<I> siblingCondition;

            public Builder<I> withId(String id) {
                this.id = id;
                return this;
            }

            public Builder<I> withOperation(AbstractStation<I, ?> operation) {
                this.station = operation;
                return this;
            }

            public Builder<I> withCondition(Condition<I> condition) {
                this.condition = condition;
                return this;
            }

            public Builder<I> withSiblingCondition(BranchCondition<I> siblingCondition) {
                this.siblingCondition = siblingCondition;
                return this;
            }

            public Branch<I> build() {
                Objects.requireNonNull(station, "branch station is required");
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException("branch id is required");
                }

                Branch<I> branch = new Branch<>();
                branch.station = station;
                branch.id = id;
                branch.condition = condition;
                branch.siblingCondition = siblingCondition;
                return branch;
            }
        }
    }
}
