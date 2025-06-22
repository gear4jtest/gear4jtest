package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;

public class UnvaryingContainerDefinition<A> extends ContainerBaseDefinition<A, A> {

	private UnvaryingContainerDefinition() {
		super(new ArrayList<>(), null);
	}

	public static class Builder<A> {

		private final UnvaryingContainerDefinition<A> managedInstance;

		public Builder() {
			managedInstance = new UnvaryingContainerDefinition<>();
		}

		public Builder<A> withOneLine(AbstractOperationDefinition<A, A> operationDefinition) {
			var branch = new Branch.Builder<A>().withOperation(operationDefinition).build();
			this.managedInstance.pipelines.add(branch);
			this.managedInstance.func = Container1Definition.Container1DFunction.identity();
			return this;
		}

		public Builder<A> withOneLine(AbstractOperationDefinition<A, A> operationDefinition, Container1Definition.Container1DFunction<A, A> function) {
			var branch = new Branch.Builder<A>().withOperation(operationDefinition).build();
			this.managedInstance.pipelines.add(branch);
			this.managedInstance.func = function;
			return this;
		}

		public Builder<A> withOneLine(AbstractOperationDefinition<A, A> operationDefinition, Condition<A> condition, Container1Definition.Container1DFunction<A, A> function) {
			var branch = new Branch.Builder<A>().withOperation(operationDefinition).withCondition(condition).build();
			this.managedInstance.pipelines.add(branch);
			this.managedInstance.func = function;
			return this;
		}

		public Builder<A> withTwoLines(Branch<A> operationDefinition, Branch<A> operationDefinition2, Container2Definition.Container2DFunction<A, A, A> function) {
			this.managedInstance.pipelines.add(operationDefinition);
			this.managedInstance.pipelines.add(operationDefinition2);
			this.managedInstance.func = function;
			return this;
		}
	}
}
