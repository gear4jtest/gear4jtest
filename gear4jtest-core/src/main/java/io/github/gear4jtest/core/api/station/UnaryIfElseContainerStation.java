package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;

import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.config.FlowConfig;

public class UnaryIfElseContainerStation<A> extends ContainerBaseStation<A, A> {
    private AbstractStation<A, A> elseOp;

    private UnaryIfElseContainerStation() {
        super(new ArrayList<>(), null);
    }

    public AbstractStation<A, A> getElseOp() {
        return elseOp;
    }

    public static class Builder<A> {
        private final UnaryIfElseContainerStation<A> managedInstance;

        public Builder() {
            managedInstance = new UnaryIfElseContainerStation<>();
        }

        public Builder<A> flowConfig(FlowConfig flowConfig) {
            this.managedInstance.setFlowConfig(flowConfig);
            return this;
        }

        public Builder<A> conditionally(AbstractStation<A, A> operationDefinition, Condition<A> condition) {
            this.managedInstance.pipelines
                    .add(new Branch.Builder<A>().withCondition(condition).withOperation(operationDefinition).build());
            return this;
        }

        public UnaryIfElseContainerStation<A> elseOp(AbstractStation<A, A> station) {
            this.managedInstance.elseOp = station;
            return this.managedInstance;
        }
    }
}
