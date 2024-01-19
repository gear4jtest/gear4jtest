package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition.ContainerFunction;

public class Container1Definition<IN, OUT, A> extends OperationDefinition<IN, OUT> {

	private List<LineDefinition<?, ?>> subLines;
	private Container1DFunction<?, ?> func;

	private Container1Definition() {
		this.subLines = new ArrayList<>();
	}

	public List<LineDefinition<?, ?>> getChildren() {
		return subLines;
	}
	
	public Container1DFunction<?, ?> getFunc() {
		return func;
	}

	public static class Builder<IN, OUT, A> {

		private final Container1Definition<IN, OUT, A> managedInstance;

		public Builder(LineDefinition<OUT, A> startingElement) {
			managedInstance = new Container1Definition<>();
			managedInstance.subLines.add(startingElement);
		}

		public Builder(LineDefinition<OUT, A> startingElement, BiPredicate<IN, ItemExecution> condition) {
			managedInstance = new Container1Definition<>();
			managedInstance.subLines.add(startingElement);
		}

		public <START, B> Container2Definition.Builder<IN, OUT, A, B> withSubLine(LineDefinition<START, B> startingElement) {
			return new Container2Definition.Builder<IN, OUT, A, B>(managedInstance.getChildren(), startingElement);
		}

		public <C> ContainerDefinition<IN, C> returns(Container1DFunction<A, C> func) {
			managedInstance.func = func;
			return new ContainerDefinition<IN, C>(managedInstance.subLines, managedInstance.func);
//			return (Container1Definition<IN, C, A>) managedInstance;
		}

		public ContainerDefinition<IN, Void> build() {
			return new ContainerDefinition<IN, Void>(managedInstance.subLines, managedInstance.func);
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
