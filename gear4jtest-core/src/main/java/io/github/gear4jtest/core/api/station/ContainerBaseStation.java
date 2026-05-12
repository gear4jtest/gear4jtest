package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
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
                throw new IllegalArgumentException("Container contains a branch without stable id");
            }

            if (!ids.add(branchId)) {
                throw new IllegalArgumentException("Container contains duplicated branch id '" + branchId + "'");
            }
        }
    }

    public List<Branch<IN>> getPipelines() {
        return pipelines;
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
        private final ContainerBaseStation<IN, OUT> managedInstance;

        public Builder() {
            this.managedInstance = new ContainerBaseStation<>(new ArrayList<>(), null);
        }

        public Builder(ExecutorService executorService) {
            this();
            this.managedInstance.isParallel = true;
            this.managedInstance.executorService = executorService;
        }

        public Builder<IN, OUT> flowConfig(FlowConfig flowConfig) {
            this.managedInstance.flowConfig = flowConfig;
            return this;
        }

        public Builder<IN, OUT> awaitTimeout(Duration awaitTimeout) {
            this.managedInstance.awaitTimeout = awaitTimeout;
            return this;
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement) {
            var branch = new Branch.Builder<IN>().withId(id).withOperation(startingElement).build();
            return new Container1Station.Builder<>(this.managedInstance, branch);
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement,
                                                                     Condition<IN> condition) {
            var branch = new Branch.Builder<IN>().withId(id).withCondition(condition).withOperation(startingElement)
                    .build();
            return new Container1Station.Builder<>(this.managedInstance, branch);
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement,
                                                                     BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>().withId(id).withSiblingCondition(siblingCondition)
                    .withOperation(startingElement).build();
            return new Container1Station.Builder<>(this.managedInstance, branch);
        }

        public <A> Container1Station.Builder<IN, OUT, A> withSubLine(String id,
                                                                     AbstractStation<IN, A> startingElement,
                                                                     Condition<IN> condition,
                                                                     BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>().withId(id).withCondition(condition)
                    .withSiblingCondition(siblingCondition).withOperation(startingElement).build();
            return new Container1Station.Builder<>(this.managedInstance, branch);
        }
    }

    public static class Branch<I> {
        private String id;
        private AbstractStation<I, ?> station;
        private Condition<I> condition;
        private BranchCondition<I> siblingCondition;

        public Branch() {
        }

        public String getId() {
            return id;
        }

        public String getEffectiveId() {
            return (id != null && !id.isBlank()) ? id : station.getId();
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
            private final Branch<I> managedInstance;

            public Builder() {
                this.managedInstance = new Branch<>();
            }

            public Builder<I> withId(String id) {
                this.managedInstance.id = id;
                return this;
            }

            public Builder<I> withOperation(AbstractStation<I, ?> operation) {
                this.managedInstance.station = operation;
                return this;
            }

            public Builder<I> withCondition(Condition<I> condition) {
                this.managedInstance.condition = condition;
                return this;
            }

            public Builder<I> withSiblingCondition(BranchCondition<I> siblingCondition) {
                this.managedInstance.siblingCondition = siblingCondition;
                return this;
            }

            public Branch<I> build() {
                Objects.requireNonNull(managedInstance.station, "branch station is required");
                if (managedInstance.id == null || managedInstance.id.isBlank()) {
                    managedInstance.id = managedInstance.station.getId();
                }
                return this.managedInstance;
            }
        }
    }
}
