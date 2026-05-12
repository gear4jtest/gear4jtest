package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class UnaryContainerStation<A> extends ContainerBaseStation<A, A> {
    private UnaryContainerStation() {
        super(new ArrayList<>(), null);
    }

    public static class Builder<A> {
        private final UnaryContainerStation<A> managedInstance;

        public Builder() {
            managedInstance = new UnaryContainerStation<>();
        }

        public Builder<A> parallel(ExecutorService executorService) {
            this.managedInstance.isParallel = true;
            this.managedInstance.executorService = executorService;
            return this;
        }

        public Builder<A> flowConfig(FlowConfig flowConfig) {
            this.managedInstance.setFlowConfig(flowConfig);
            return this;
        }

        public Builder<A> awaitTimeout(Duration awaitTimeout) {
            this.managedInstance.setAwaitTimeout(awaitTimeout);
            return this;
        }

        public Builder<A> withOneLine(AbstractStation<A, A> operationDefinition) {
            var branch = new Branch.Builder<A>().withOperation(operationDefinition).build();
            this.managedInstance.pipelines.add(branch);
            this.managedInstance.func = Container1Station.Container1DFunction.identity();
            return this;
        }

        public Builder<A> withOneLine(AbstractStation<A, A> operationDefinition,
                                      Container1Station.Container1DFunction<A, A> function) {
            var branch = new Branch.Builder<A>().withOperation(operationDefinition).build();
            this.managedInstance.pipelines.add(branch);
            this.managedInstance.func = function;
            return this;
        }

        public Builder<A> withOneLine(AbstractStation<A, A> operationDefinition,
                                      Condition<A> condition,
                                      Container1Station.Container1DFunction<A, A> function) {
            var branch = new Branch.Builder<A>().withOperation(operationDefinition).withCondition(condition).build();
            this.managedInstance.pipelines.add(branch);
            this.managedInstance.func = function;
            return this;
        }

        public Builder<A> withTwoLines(Branch<A> operationDefinition,
                                       Branch<A> operationDefinition2,
                                       Container2Station.Container2DFunction<A, A, A> function) {
            this.managedInstance.pipelines.add(operationDefinition);
            this.managedInstance.pipelines.add(operationDefinition2);
            this.managedInstance.func = function;
            return this;
        }
    }
}
