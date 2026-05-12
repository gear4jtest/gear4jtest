package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;

public class Container2Station<IN, OUT, A, B> extends ContainerBaseStation<IN, OUT> {

    private Container2Station(List<Branch<IN>> subLines) {
        super(new ArrayList<>(2), null);
        this.pipelines.add(subLines.get(0));
    }

    @FunctionalInterface
    public interface Container2DFunction<A, B, C> extends ContainerFunction<C> {
        C applya(A a, B b);

        @Override
        @SuppressWarnings("unchecked")
        default C apply(Object... objects) {
            assert objects != null && objects.length == 2;
            return applya((A) objects[0], (B) objects[1]);
        }
    }

    public static class Builder<IN, OUT, A, B> {

        private final Container2Station<IN, OUT, A, B> managedInstance;

        public Builder(ContainerBaseStation<IN, OUT> parentDefinition, Branch<IN> newLine) {
            managedInstance = new Container2Station<>(parentDefinition.pipelines);
            managedInstance.pipelines.add(newLine);
            managedInstance.executorService = parentDefinition.getExecutorService();
            managedInstance.isParallel = parentDefinition.isParallel();
            managedInstance.setFlowConfig(parentDefinition.getFlowConfig());
            managedInstance.setAwaitTimeout(parentDefinition.getAwaitTimeout());
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <C> ContainerBaseStation<IN, C> returns(Container2DFunction<A, B, C> func) {
            ContainerBaseStation.validateUniqueBranchIds(managedInstance.getPipelines());
            managedInstance.func = (ContainerBaseStation.ContainerFunction) func;
            return (ContainerBaseStation<IN, C>) this.managedInstance;
        }

        @SuppressWarnings("unchecked")
        public ContainerBaseStation<IN, Void> build() {
            ContainerBaseStation.validateUniqueBranchIds(managedInstance.getPipelines());
            return (ContainerBaseStation<IN, Void>) this.managedInstance;
        }
    }
}
