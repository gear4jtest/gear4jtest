package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;

public class UnvaryingIfElseContainerDefinition<A> extends ContainerBaseDefinition<A, A> {
	private OperationDefinition<A, A> elseOp;

	private UnvaryingIfElseContainerDefinition() {
		super(new ArrayList<>(), null);
	}

	@Override
	public A execute(A input, ExecutionContext context, OperationExecution operationExecution) {
		boolean conditionMet = false;
		A containerResult = null;

		for (Branch<A> element : pipelines) {
			if (element.getCondition() == null || element.getCondition().test(input, context)) {
				conditionMet = true;
				A newObject = deepClone(input);
				var result = element.getOperation().run(newObject, context);
				if (result.getReport().getStatus() == OperationExecution.OperationReport.Status.FAILED) {
					operationExecution.getReport().complete();
					return null;
				}

				containerResult = (A) result.getResult();
				break;
			}
		}

		if (!conditionMet && elseOp != null) {
			A newObject = deepClone(input);
			var result = elseOp.run(newObject, context);
			if (result.getReport().getStatus() == OperationExecution.OperationReport.Status.FAILED) {
				operationExecution.getReport().complete();
				return null;
			}
			containerResult = (A) result.getResult();
		}

		return containerResult;
	}

	public static class Builder<A> {

		private final UnvaryingIfElseContainerDefinition<A> managedInstance;

		public Builder() {
			managedInstance = new UnvaryingIfElseContainerDefinition<>();
		}

		public Builder<A> conditionally(AbstractOperationDefinition<A, A> operationDefinition, Condition<A> condition) {
			this.managedInstance.pipelines.add(new Branch.Builder<A>().withCondition(condition).withOperation(operationDefinition).build());
			return this;
		}

		public UnvaryingIfElseContainerDefinition<A> elseOp(OperationDefinition<A, A> operationDefinition) {
			this.managedInstance.elseOp = operationDefinition;
			return this.managedInstance;
		}

	}
}
