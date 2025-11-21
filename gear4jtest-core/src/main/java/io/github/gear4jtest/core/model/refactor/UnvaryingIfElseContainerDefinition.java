package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

public class UnvaryingIfElseContainerDefinition<A> extends ContainerBaseDefinition<A, A> {
	private OperationDefinition<A, A> elseOp;

	private UnvaryingIfElseContainerDefinition() {
		super(new ArrayList<>(), null);
	}

	@Override
	public A doExecute(A input, ExecutionContext context, OperationExecutionContext operationExecution) {
		boolean conditionMet = false;
		A containerResult = null;

		for (Branch<A> element : pipelines) {
			if (element.getCondition() == null || element.getCondition().test(input, context)) {
				conditionMet = true;
				A newObject = deepClone(input);
				var rec = element.getOperation().run(newObject, context);
                rec.setParentOperationId(operationExecution.getOperationId());
                context.getExecutionManager().append(rec);
				if (rec.getStatus() == OperationExecutionRecord.Status.FAILED) {
					operationExecution.getRecord().markFailed(null);
					return null;
				}

				containerResult = (A) rec.getOutput(Object.class);
				break;
			}
		}

		if (!conditionMet && elseOp != null) {
			A newObject = deepClone(input);
			var recElse = elseOp.run(newObject, context);
            recElse.setParentOperationId(operationExecution.getOperationId());
            context.getExecutionManager().append(recElse);
			if (recElse.getStatus() == OperationExecutionRecord.Status.FAILED) {
				operationExecution.getRecord().markFailed(null);
				return null;
			}
			containerResult = (A) recElse.getOutput(Object.class);
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
