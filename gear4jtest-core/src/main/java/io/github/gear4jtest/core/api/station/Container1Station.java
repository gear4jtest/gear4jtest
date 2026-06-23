package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.BranchCondition;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class Container1Station<IN, OUT, A> extends ContainerBaseStation<IN, OUT> {
    private Container1Station() {
        super(List.of(), null);
    }

    @FunctionalInterface
    public interface Container1DFunction<A, B> extends ContainerFunction<B> {
        static <T> Container1DFunction<T, T> identity() {
            return t -> t;
        }

        B applya(A a);

        @Override
        @SuppressWarnings("unchecked")
        default B apply(Object... objects) {
            if (objects == null || objects.length != 1) {
                throw new IllegalArgumentException("Expected exactly one container result");
            }
            return applya((A) objects[0]);
        }
    }

    public static class Builder<IN, OUT, A> {
        final List<Branch<IN>> branches = new ArrayList<>();
        boolean isParallel;
        ExecutorService executorService;
        FlowConfig flowConfig;
        Duration awaitTimeout;

        public Builder(ContainerBaseStation.Builder<IN, OUT> parent, Branch<IN> branch) {
            this.branches.addAll(parent.branches);
            this.branches.add(branch);
            this.isParallel = parent.isParallel;
            this.executorService = parent.executorService;
            this.flowConfig = parent.flowConfig;
            this.awaitTimeout = parent.awaitTimeout;
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition) {
            var branch = new Branch.Builder<IN>()
                    .withId(id)
                    .withOperation(operationDefinition)
                    .build();
            return new Container2Station.Builder<>(this, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition,
                                                                        Condition<IN> condition) {
            var branch = new Branch.Builder<IN>()
                    .withId(id)
                    .withCondition(condition)
                    .withOperation(operationDefinition)
                    .build();
            return new Container2Station.Builder<>(this, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition,
                                                                        BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>()
                    .withId(id)
                    .withSiblingCondition(siblingCondition)
                    .withOperation(operationDefinition)
                    .build();
            return new Container2Station.Builder<>(this, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition,
                                                                        Condition<IN> condition,
                                                                        BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>()
                    .withId(id)
                    .withCondition(condition)
                    .withSiblingCondition(siblingCondition)
                    .withOperation(operationDefinition).build();
            return new Container2Station.Builder<>(this, branch);
        }

        public <C> ContainerBaseStation<IN, C> returns(Container1DFunction<A, C> func) {
            return buildStation(branches, isParallel, executorService, flowConfig, awaitTimeout, func);
        }

        public ContainerBaseStation<IN, Void> build() {
            return buildStation(branches, isParallel, executorService, flowConfig, awaitTimeout, null);
        }
    }
}
