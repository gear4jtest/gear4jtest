package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;

import io.github.gear4jtest.core.api.behavior.BranchCondition;
import io.github.gear4jtest.core.api.behavior.Condition;

public class Container1Station<IN, OUT, A> extends ContainerBaseStation<IN, OUT> {
    private Container1Station() {
        super(new ArrayList<>(1), null);
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
            assert objects != null && objects.length == 1;
            return applya((A) objects[0]);
        }
    }

    public static class Builder<IN, OUT, A> {
        private final Container1Station<IN, OUT, A> managedInstance;

        public Builder(ContainerBaseStation<IN, OUT> parentDefinition, Branch<IN> branch) {
            managedInstance = new Container1Station<>();
            managedInstance.pipelines.add(branch);
            managedInstance.executorService = parentDefinition.getExecutorService();
            managedInstance.isParallel = parentDefinition.isParallel();
            managedInstance.setFlowConfig(parentDefinition.getFlowConfig());
            managedInstance.setAwaitTimeout(parentDefinition.getAwaitTimeout());
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition) {
            var branch = new Branch.Builder<IN>().withId(id).withOperation(operationDefinition).build();
            return new Container2Station.Builder<>(managedInstance, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition,
                                                                        Condition<IN> condition) {
            var branch = new Branch.Builder<IN>().withId(id).withCondition(condition).withOperation(operationDefinition)
                    .build();
            return new Container2Station.Builder<>(managedInstance, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition,
                                                                        BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>().withId(id).withSiblingCondition(siblingCondition)
                    .withOperation(operationDefinition).build();
            return new Container2Station.Builder<>(managedInstance, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(String id,
                                                                        AbstractStation<IN, B> operationDefinition,
                                                                        Condition<IN> condition,
                                                                        BranchCondition<IN> siblingCondition) {
            var branch = new Branch.Builder<IN>().withId(id).withCondition(condition)
                    .withSiblingCondition(siblingCondition).withOperation(operationDefinition).build();
            return new Container2Station.Builder<>(managedInstance, branch);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <C> ContainerBaseStation<IN, C> returns(Container1DFunction<A, C> func) {
            ContainerBaseStation.validateUniqueBranchIds(managedInstance.getPipelines());
            managedInstance.func = (ContainerFunction) func;
            return (ContainerBaseStation<IN, C>) this.managedInstance;
        }

        @SuppressWarnings("unchecked")
        public ContainerBaseStation<IN, Void> build() {
            ContainerBaseStation.validateUniqueBranchIds(managedInstance.getPipelines());
            return (ContainerBaseStation<IN, Void>) this.managedInstance;
        }
    }
}
