package io.github.gear4jtest.core.api.station;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.behavior.Condition;

public class ContainerBaseStation<IN, OUT> extends AbstractStation<IN, OUT> {

	protected final List<Branch<IN>> pipelines;
	protected ContainerFunction<OUT> func;
	protected boolean isParallel = false;
	protected ExecutorService executorService;

	public ContainerBaseStation(List<Branch<IN>> pipelines, ContainerFunction<OUT> func) {
		super("", StationKind.CONTAINER);
		this.pipelines = pipelines;
		this.func = func;
	}

	public List<Branch<IN>> getPipelines() {
		return pipelines;
	}

	public ContainerFunction<OUT> getFunc() {
		return func;
	}

	public boolean isParallel() {
		return isParallel;
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}

	public static class Builder<IN, OUT> {
		private final ContainerBaseStation<IN, OUT> managedInstance;

		public Builder() {
			this.managedInstance = new ContainerBaseStation<>(new ArrayList<>(), null);
		}

		public Builder(ExecutorService executorService) {
			this();
			this.managedInstance.isParallel = true;
			this.managedInstance.executorService = executorService;
		}

		public <A> Container1Station.Builder<IN, OUT, A> withSubLine(AbstractStation<IN, A> startingElement) {
			var branch = new Branch.Builder<IN>().withOperation(startingElement).build();
			return new Container1Station.Builder<>(this.managedInstance, branch);
		}

		public <A> Container1Station.Builder<IN, OUT, A> withSubLine(AbstractStation<IN, A> startingElement, Condition<IN> condition) {
			var branch = new Branch.Builder<IN>().withCondition(condition).withOperation(startingElement).build();
			return new Container1Station.Builder<>(this.managedInstance, branch);
		}
	}

	@FunctionalInterface
	public interface ContainerFunction<OUT> {
		OUT apply(Object... objects);
	}

	public static class Branch<I> {
		private String id;
		private AbstractStation<I, ?> station;
		private Condition<I> condition;

		public Branch() {
		}

		public String getId() {
			return id;
		}

		public AbstractStation<I, ?> getStation() {
			return station;
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
				this.managedInstance.station = operation;
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
