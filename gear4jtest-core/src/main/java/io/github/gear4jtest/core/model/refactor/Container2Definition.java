package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

public class Container2Definition<IN, OUT, A, B> extends ContainerBaseDefinition<IN, OUT> {

	private Container2Definition(List<Branch<IN>> subLines) {
		super(new ArrayList<>(2), null);
		this.pipelines.add(subLines.get(0));
	}

	public static class Builder<IN, OUT, A, B> {

		private final Container2Definition<IN, OUT, A, B> managedInstance;

		public Builder(ContainerBaseDefinition<IN, OUT> parentDefinition, Branch<IN> newLine) {
			managedInstance = new Container2Definition<>(parentDefinition.pipelines);
			managedInstance.pipelines.add(newLine);
			managedInstance.executorService = parentDefinition.executorService;
			managedInstance.isParallel = parentDefinition.isParallel;
		}

		// To be continued
//		public <START> Builder<START, Void> withSubLine(OperationDefinition<START, ?> startingElement) {
//			managedInstance.subLines.add(startingElement);
//			return (Builder<START, Void>) this;
//		}

		public <C> ContainerBaseDefinition<IN, C> returns(Container2DFunction<A, B, C> func) {
			managedInstance.func = func;
			return (ContainerBaseDefinition<IN, C>) this.managedInstance;
//			return new ContainerDefinition<>(managedInstance.subLines, managedInstance.func);
		}

		public ContainerBaseDefinition<IN, Void> build() {
			return (ContainerBaseDefinition<IN, Void>) this.managedInstance;
//			return new ContainerDefinition<>(managedInstance.subLines, managedInstance.func);
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
