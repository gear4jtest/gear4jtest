package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

public class Container1Definition<IN, OUT, A> extends ContainerBaseDefinition<IN, OUT> {

	private Container1Definition() {
		super(new ArrayList<>(1), null);
	}

	public static class Builder<IN, OUT, A> {

		private final Container1Definition<IN, OUT, A> managedInstance;

		public Builder(LineDefinition<OUT, A> lineDefinition) {
			managedInstance = new Container1Definition<>();
			managedInstance.subLines.add(lineDefinition);
		}

		public <START, B> Container2Definition.Builder<IN, OUT, A, B> withSubLine(LineDefinition<START, B> lineDefinition) {
			return new Container2Definition.Builder<>(managedInstance.getSubLines(), lineDefinition);
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

		default Object apply(Object... objects) {
			assert objects != null && objects.length == 1;
			return applya((A) objects[0]);
		}
	}
	
}
