package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class UnaryContainerStation<A> extends ContainerBaseStation<A, A> {
    private UnaryContainerStation() {
        super(new ArrayList<>(), null);
    }

    public static class Builder<A> {
        private final List<Branch<A>> branches = new ArrayList<>();
        private boolean isParallel;
        private ExecutorService executorService;
        private FlowConfig flowConfig;
        private Duration awaitTimeout;
        private ContainerFunction<A> function;

        public Builder<A> parallel(ExecutorService executorService) {
            this.isParallel = true;
            this.executorService = executorService;
            return this;
        }

        public Builder<A> flowConfig(FlowConfig flowConfig) {
            this.flowConfig = flowConfig;
            return this;
        }

        public Builder<A> awaitTimeout(Duration awaitTimeout) {
            this.awaitTimeout = awaitTimeout;
            return this;
        }

        public Builder<A> withOneLine(String id, AbstractStation<A, A> operationDefinition) {
            var branch = new Branch.Builder<A>().withId(id).withOperation(operationDefinition).build();
            this.branches.add(branch);
            this.function = Container1Station.Container1DFunction.identity();
            return this;
        }

        public Builder<A> withOneLine(String id,
                                      AbstractStation<A, A> operationDefinition,
                                      Container1Station.Container1DFunction<A, A> function) {
            var branch = new Branch.Builder<A>().withId(id).withOperation(operationDefinition).build();
            this.branches.add(branch);
            this.function = function;
            return this;
        }

        public Builder<A> withOneLine(String id,
                                      AbstractStation<A, A> operationDefinition,
                                      Condition<A> condition,
                                      Container1Station.Container1DFunction<A, A> function) {
            var branch = new Branch.Builder<A>().withId(id).withOperation(operationDefinition).withCondition(condition)
                    .build();
            this.branches.add(branch);
            this.function = function;
            return this;
        }

        public Builder<A> withTwoLines(Branch<A> operationDefinition,
                                       Branch<A> operationDefinition2,
                                       Container2Station.Container2DFunction<A, A, A> function) {
            this.branches.add(operationDefinition);
            this.branches.add(operationDefinition2);
            this.function = function;
            return this;
        }

        public UnaryContainerStation<A> build() {
            ContainerBaseStation.validateUniqueBranchIds(branches);
            UnaryContainerStation<A> station = new UnaryContainerStation<>();
            station.pipelines.addAll(branches);
            station.func = function;
            station.isParallel = isParallel;
            station.executorService = executorService;
            station.setFlowConfig(flowConfig);
            station.setAwaitTimeout(awaitTimeout);
            return station;
        }
    }
}
