package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.ContainerDefinition.ContainerFunction;

public class Container2Definition<IN, OUT, A, B> extends OperationDefinition<IN, OUT> {

	private List<LineDefinition<?, ?>> subLines;
	private Container2DFunction<?, ?, ?> func;

	private Container2Definition(List<LineDefinition<?, ?>> subLines) {
		this.subLines = new ArrayList<>();
		this.subLines.addAll(subLines);
	}

	public List<LineDefinition<?, ?>> getChildren() {
		return subLines;
	}
	
	public Container2DFunction<?, ?, ?> getFunc() {
		return func;
	}

	public static class Builder<IN, OUT, A, B> {

		private final Container2Definition<IN, OUT, A, B> managedInstance;

		public Builder(List<LineDefinition<?, ?>> subLines, LineDefinition<?, ?> newLine) {
			managedInstance = new Container2Definition<>(subLines);
			managedInstance.subLines.add(newLine);
		}

		// To be continued
//		public <START> Builder<START, Void> withSubLine(LineDefinition<START, ?> startingElement) {
//			managedInstance.subLines.add(startingElement);
//			return (Builder<START, Void>) this;
//		}

		public <C> ContainerDefinition<IN, C> returns(Container2DFunction<A, B, C> func) {
			managedInstance.func = func;
			return new ContainerDefinition<IN, C>(managedInstance.subLines, managedInstance.func);
//			return (Container2Definition<IN, C, A, B>) managedInstance;
		}

		public ContainerDefinition<IN, Void> build() {
			return new ContainerDefinition<IN, Void>(managedInstance.subLines, managedInstance.func);
//			return (Container2Definition<IN, Void, A, B>) managedInstance;
		}

	}

	@FunctionalInterface
	public interface Container2DFunction<A, B, C> extends ContainerFunction {
		C applya(A a, B b);
		
		default Object apply(Object... objects) {
			assert objects != null && objects.length == 2;
			return applya((A) objects[0], (B) objects[1]);
		}
	}

}
