package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;

import io.github.gear4jtest.core.api.behavior.Condition;

public class Container1Station<IN, OUT, A> extends ContainerBaseStation<IN, OUT> {

    private Container1Station() {
        super(new ArrayList<>(1), null);
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

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(AbstractStation<IN, B> operationDefinition) {
            var branch = new Branch.Builder<IN>().withOperation(operationDefinition).build();
            return new Container2Station.Builder<>(managedInstance, branch);
        }

        public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(
                AbstractStation<IN, B> operationDefinition,
                Condition<IN> condition) {
            var branch = new Branch.Builder<IN>()
                    .withCondition(condition)
                    .withOperation(operationDefinition)
                    .build();
            return new Container2Station.Builder<>(managedInstance, branch);
        }

        @SuppressWarnings("unchecked")
        public <C> ContainerBaseStation<IN, C> returns(Container1DFunction<A, C> func) {
            managedInstance.func = func;
            return (ContainerBaseStation<IN, C>) this.managedInstance;
        }

        @SuppressWarnings("unchecked")
        public ContainerBaseStation<IN, Void> build() {
            return (ContainerBaseStation<IN, Void>) this.managedInstance;
        }
    }

    @FunctionalInterface
    public interface Container1DFunction<A, B> extends ContainerFunction {
        B applya(A a);

        static <T> Container1DFunction<T, T> identity() {
            return t -> t;
        }

        @Override
        default B apply(Object... objects) {
            assert objects != null && objects.length == 1;
            return applya((A) objects[0]);
        }
    }
}
