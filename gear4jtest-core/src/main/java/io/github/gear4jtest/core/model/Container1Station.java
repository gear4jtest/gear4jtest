package io.github.gear4jtest.core.model;

import java.util.ArrayList;

public class Container1Station<IN, OUT, A> extends ContainerBaseStation<IN, OUT> {

	private Container1Station() {
		super(new ArrayList<>(1), null);
	}

	public static class Builder<IN, OUT, A> {

		private final Container1Station<IN, OUT, A> managedInstance;

		public Builder(ContainerBaseStation<IN, OUT> parentDefinition, Branch<IN> branch) {
			managedInstance = new Container1Station<>();
			managedInstance.pipelines.add(branch);
			managedInstance.executorService = parentDefinition.executorService;
			managedInstance.isParallel = parentDefinition.isParallel;
		}

		public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(AbstractStation<IN, B> operationDefinition) {
			var branch = new Branch.Builder<IN>().withOperation(operationDefinition).build();
			return new Container2Station.Builder<>(managedInstance, branch);
		}

		public <B> Container2Station.Builder<IN, OUT, A, B> withSubLine(AbstractStation<IN, B> operationDefinition, Condition<IN> condition) {
			var branch = new Branch.Builder<IN>().withCondition(condition).withOperation(operationDefinition).build();
			return new Container2Station.Builder<>(managedInstance, branch);
		}

		public <C> ContainerBaseStation<IN, C> returns(Container1DFunction<A, C> func) {
			managedInstance.func = func;
			return (ContainerBaseStation<IN, C>) this.managedInstance;
//			return new ContainerDefinition<>(managedInstance.subLines, managedInstance.func);
		}

		public ContainerBaseStation<IN, Void> build() {
			return (ContainerBaseStation<IN, Void>) this.managedInstance;
//			return new ContainerDefinition<IN, Void>(managedInstance.subLines, managedInstance.func);
//			return (Container1Definition<IN, Void, A>) managedInstance;
		}

	}

	@FunctionalInterface
	public interface Container1DFunction<A, B> extends ContainerFunction {
		B applya(A a);

		static <T> Container1DFunction<T, T> identity() {
			return t -> t;
		}

		default B apply(Object... objects) {
			assert objects != null && objects.length == 1;
			return applya((A) objects[0]);
		}
	}

}
