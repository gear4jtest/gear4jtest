package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class UnaryContainerStation<A> extends ContainerBaseStation<A, A> {
    private UnaryContainerStation(List<Branch<A>> branches,
                                  ContainerResultsFunction<A> function,
                                  boolean parallel,
                                  ExecutorService executorService,
                                  FlowConfig flowConfig,
                                  Duration awaitTimeout) {
        super(branches, function, parallel, executorService, flowConfig, awaitTimeout, true);
    }

    public static class Builder<A> {
        private final List<Branch<A>> branches = new ArrayList<>();
        private boolean isParallel;
        private ExecutorService executorService;
        private FlowConfig flowConfig;
        private Duration awaitTimeout;
        private ContainerResultsFunction<A> function;

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
            this.function = results -> cast(results.get(id));
            return this;
        }

        public Builder<A> withOneLine(String id,
                                      AbstractStation<A, A> operationDefinition,
                                      Function<A, A> function) {
            var branch = new Branch.Builder<A>().withId(id).withOperation(operationDefinition).build();
            this.branches.add(branch);
            this.function = results -> function.apply(cast(results.get(id)));
            return this;
        }

        public Builder<A> withOneLine(String id,
                                      AbstractStation<A, A> operationDefinition,
                                      Condition<A> condition,
                                      Function<A, A> function) {
            var branch = new Branch.Builder<A>().withId(id).withOperation(operationDefinition).withCondition(condition)
                    .build();
            this.branches.add(branch);
            this.function = results -> function.apply(cast(results.get(id)));
            return this;
        }

        public Builder<A> withTwoLines(Branch<A> operationDefinition,
                                       Branch<A> operationDefinition2,
                                       BiFunction<A, A, A> function) {
            this.branches.add(operationDefinition);
            this.branches.add(operationDefinition2);
            this.function = results -> function.apply(cast(results.get(operationDefinition.getId())),
                                                      cast(results.get(operationDefinition2.getId())));
            return this;
        }

        public UnaryContainerStation<A> build() {
            ContainerBaseStation.validateUniqueBranchIds(branches);
            return new UnaryContainerStation<>(branches, function, isParallel, executorService, flowConfig,
                    awaitTimeout);
        }

        @SuppressWarnings("unchecked")
        private static <A> A cast(Object value) {
            return (A) value;
        }
    }
}
