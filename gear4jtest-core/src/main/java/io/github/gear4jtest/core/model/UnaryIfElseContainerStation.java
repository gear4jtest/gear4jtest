package io.github.gear4jtest.core.model;

import java.util.ArrayList;

import io.github.gear4jtest.core.persistence.StationLog;

public class UnaryIfElseContainerStation<A> extends ContainerBaseStation<A, A> {
	private AbstractStation<A, A> elseOp;

	private UnaryIfElseContainerStation() {
		super(new ArrayList<>(), null);
	}

	public AbstractStation<A, A> getElseOp() {
		return elseOp;
	}

//	@Override
//	public A doExecute(A input, ExecutionContext context, StationExecutionContext operationExecution) {
//		boolean conditionMet = false;
//		A containerResult = null;
//
//		for (Branch<A> element : pipelines) {
//			if (element.getCondition() == null || element.getCondition().test(input, context)) {
//				conditionMet = true;
//				A newObject = deepClone(input);
//				var rec = element.getStation().run(newObject, context);
//                rec.setParentOperationId(operationExecution.getOperationId());
////                context.getExecutionManager().append(rec);
//				if (rec.getStatus() == StationLog.Status.FAILED) {
//					operationExecution.getRecord().markFailed(null);
//					return null;
//				}
//
//				containerResult = (A) rec.getOutput(Object.class);
//				break;
//			}
//		}
//
//		if (!conditionMet && elseOp != null) {
//			A newObject = deepClone(input);
//			var recElse = elseOp.run(newObject, context);
//            recElse.setParentOperationId(operationExecution.getOperationId());
////            context.getExecutionManager().append(recElse);
//			if (recElse.getStatus() == StationLog.Status.FAILED) {
//				operationExecution.getRecord().markFailed(null);
//				return null;
//			}
//			containerResult = (A) recElse.getOutput(Object.class);
//		}
//
//		return containerResult;
//	}

	public static class Builder<A> {

		private final UnaryIfElseContainerStation<A> managedInstance;

		public Builder() {
			managedInstance = new UnaryIfElseContainerStation<>();
		}

		public Builder<A> conditionally(AbstractStation<A, A> operationDefinition, Condition<A> condition) {
			this.managedInstance.pipelines.add(new Branch.Builder<A>().withCondition(condition).withOperation(operationDefinition).build());
			return this;
		}

		public UnaryIfElseContainerStation<A> elseOp(AbstractStation<A, A> station) {
			this.managedInstance.elseOp = station;
			return this.managedInstance;
		}

	}
}
