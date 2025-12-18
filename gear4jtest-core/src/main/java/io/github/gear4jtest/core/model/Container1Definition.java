package io.github.gear4jtest.core.model;

import java.util.ArrayList;

public class Container1Definition<IN, OUT, A> extends ContainerBaseDefinition<IN, OUT> {

	private Container1Definition() {
		super(new ArrayList<>(1), null);
	}

	public static class Builder<IN, OUT, A> {

		private final Container1Definition<IN, OUT, A> managedInstance;

		public Builder(ContainerBaseDefinition<IN, OUT> parentDefinition, Branch<IN> branch) {
			managedInstance = new Container1Definition<>();
			managedInstance.pipelines.add(branch);
			managedInstance.executorService = parentDefinition.executorService;
			managedInstance.isParallel = parentDefinition.isParallel;
		}

		public <B> Container2Definition.Builder<IN, OUT, A, B> withSubLine(AbstractStation<IN, B> operationDefinition) {
			var branch = new Branch.Builder<IN>().withOperation(operationDefinition).build();
			return new Container2Definition.Builder<>(managedInstance, branch);
		}

		public <B> Container2Definition.Builder<IN, OUT, A, B> withSubLine(AbstractStation<IN, B> operationDefinition, Condition<IN> condition) {
			var branch = new Branch.Builder<IN>().withCondition(condition).withOperation(operationDefinition).build();
			return new Container2Definition.Builder<>(managedInstance, branch);
		}

		public <C> ContainerBaseDefinition<IN, C> returns(Container1DFunction<A, C> func) {
			managedInstance.func = func;
			return (ContainerBaseDefinition<IN, C>) this.managedInstance;
//			return new ContainerDefinition<>(managedInstance.subLines, managedInstance.func);
		}

		public ContainerBaseDefinition<IN, Void> build() {
			return (ContainerBaseDefinition<IN, Void>) this.managedInstance;
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
