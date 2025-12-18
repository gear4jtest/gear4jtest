package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import io.github.gear4jtest.core.persistence.StationLog;

public class ContainerBaseDefinition<IN, OUT> extends AbstractStation<IN, OUT> {

	protected final List<Branch<IN>> pipelines;
	protected ContainerFunction<OUT> func;
	protected boolean isParallel = false;
	protected ExecutorService executorService;

	public ContainerBaseDefinition(List<Branch<IN>> pipelines, ContainerFunction<OUT> func) {
		super("", StationKind.CONTAINER);
		this.pipelines = pipelines;
		this.func = func;
	}

	@Override
	public OUT doExecute(IN input, ExecutionContext context, StationExecutionContext operationExecution) {
		Collection<Object> results = new ArrayList<>();
		String currentItemId = context.getCurrentItemId();

		if (isParallel && executorService != null) {
			List<Callable<Object>> tasks = new ArrayList<>();

			for (Branch<IN> element : pipelines) {
				tasks.add(() -> context.withItemId(currentItemId, () -> {
					IN newObject = deepClone(input);
					var rec = element.getOperation().run(newObject, context);

					rec.setParentOperationId(operationExecution.getOperationId());
//					context.getExecutionManager().append(rec);

					if (rec.getStatus() == StationLog.Status.FAILED) {
						// On marque l'opération globale en échec
						operationExecution.getRecord().markFailed(null);
						return null; // pas de résultat pour cette branche
					}

					return rec.getOutput(Object.class);
				}));
			}

			try {
				// Lance toutes les tâches et attend qu’elles soient terminées
				List<Future<Object>> futures = executorService.invokeAll(tasks);

				for (Future<Object> future : futures) {
					Object value = future.get(); // bloque jusqu'à fin de la tâche
					if (value != null) {
						results.add(value);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			} catch (ExecutionException e) {
				throw new RuntimeException("Erreur dans une sous-ligne du container", e.getCause());
			}

			// ⚠ À toi de décider si on shutdown ici ou non
			// Si l'executor est fourni de l'extérieur (comme dans ton test),
			// je te conseille de NE PAS le shutdown dans le container.
			// executorService.shutdown();

		} else {
			// Version séquentielle inchangée
			for (Branch<IN> element : pipelines) {
				IN newObject = deepClone(input);
				var rec = element.getOperation().run(newObject, context);
				rec.setParentOperationId(operationExecution.getOperationId());
//				context.getExecutionManager().append(rec);

				if (rec.getStatus() == StationLog.Status.FAILED) {
					operationExecution.getRecord().markFailed(null);
					return null;
				}

				results.add(rec.getOutput(Object.class));
			}
		}

		return returns(results);
	}

	private OUT returns(Collection<Object> executions) {
		var returnedObjects = executions.toArray();
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

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(AbstractStation<IN, A> startingElement) {
			var branch = new Branch.Builder<IN>().withOperation(startingElement).build();
			return new Container1Definition.Builder<>(this.managedInstance, branch);
		}

		public <A> Container1Definition.Builder<IN, OUT, A> withSubLine(AbstractStation<IN, A> startingElement, Condition<IN> condition) {
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
		private AbstractStation<I, ?> operation;
		private Condition<I> condition;

		public Branch() {
		}

		public String getId() {
			return id;
		}

		public AbstractStation<I, ?> getOperation() {
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

			public Builder<I> withOperation(AbstractStation<I, ?> operation) {
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
