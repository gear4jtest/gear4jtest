package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ContainerBaseDefinition<IN, OUT> extends AbstractOperationDefinition<IN, OUT> {

	protected final List<Branch<IN>> pipelines;
	protected ContainerFunction<OUT> func;
	protected boolean isParallel = false;
	protected ExecutorService executorService;

	public ContainerBaseDefinition(List<Branch<IN>> pipelines, ContainerFunction<OUT> func) {
		super("");
		this.pipelines = pipelines;
		this.func = func;
	}

	@Override
	public OUT execute(IN input, ExecutionContext context, OperationExecution operationExecution) {
		Collection<OperationResult<?>> results = new ArrayList<>();
		if (isParallel && executorService != null) {
			List<Runnable> tasks = new ArrayList<>();
			for (Branch<IN> element : pipelines) {
				tasks.add(() -> {
					IN newObject = deepClone(input);
					var result = element.getOperation().run(newObject, context);
					if (result.getReport().getStatus() == OperationExecution.OperationReport.Status.FAILED) {
						operationExecution.getReport().complete();
						return;
					}
					results.add(result);
				});
			}
			tasks.forEach(executorService::submit);
			executorService.shutdown();
		} else {
			for (Branch<IN> element : pipelines) {
				IN newObject = deepClone(input);
				var result = element.getOperation().run(newObject, context);

				if (result.getReport().getStatus() == OperationExecution.OperationReport.Status.FAILED) {
					operationExecution.getReport().complete();
					return null;
				}

				results.add(result);
			}
		}
		return returns(results);
	}

	private OUT returns(Collection<OperationResult<?>> executions) {
		var returnedObjects = executions.stream()
				.map(OperationResult::getResult)
				.toArray();
		if (func != null) {
			return func.apply(returnedObjects);
		} else {
			return null;
		}
	}

	<T> T deepClone(T object) {
		return object;
	}

	public static class Builder<IN, OUT> {
		private final ContainerBaseDefinition<IN, OUT> managedInstance;

		public Builder() {
			this.managedInstance = new ContainerBaseDefinition<>(new ArrayList<>(), null);
		}

		public Builder(ExecutorService executorService) {
			this();
			this.managedInstance.isParallel = true;
			this.managedInstance.executorService = executorService;
		}

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(AbstractOperationDefinition<IN, A> startingElement) {
			var branch = new Branch.Builder<IN>().withOperation(startingElement).build();
			return new Container1Definition.Builder<>(this.managedInstance, branch);
		}

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(AbstractOperationDefinition<IN, A> startingElement, Condition<IN> condition) {
			var branch = new Branch.Builder<IN>().withCondition(condition).withOperation(startingElement).build();
			return new Container1Definition.Builder<>(this.managedInstance, branch);
		}
	}

	@FunctionalInterface
	public interface ContainerFunction<OUT> {
		OUT apply(Object... objects);
	}

	public static class Branch<I> {
		private String id;
		private AbstractOperationDefinition<I, ?> operation;
		private Condition<I> condition;

		public Branch() {
		}

		public String getId() {
			return id;
		}

		public AbstractOperationDefinition<I, ?> getOperation() {
			return operation;
		}

		public Condition<I> getCondition() {
			return condition;
		}

		public static class Builder<I> {
			private final Branch<I> managedInstance;

			public Builder() {
				this.managedInstance = new Branch<>();
			}

			public Builder<I> withId(String id) {
				this.managedInstance.id = id;
				return this;
			}

			public Builder<I> withOperation(AbstractOperationDefinition<I, ?> operation) {
				this.managedInstance.operation = operation;
				return this;
			}

			public Builder<I> withCondition(Condition<I> condition) {
				this.managedInstance.condition = condition;
				return this;
			}

			public Branch<I> build() {
				return this.managedInstance;
			}
		}
	}
}
