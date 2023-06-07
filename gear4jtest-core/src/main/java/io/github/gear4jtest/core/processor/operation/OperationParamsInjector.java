package io.github.gear4jtest.core.processor.operation;

import java.util.Iterator;
import java.util.List;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.OperationModel.ParameterModel;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.ProcessorChain.ProcessorDriver;
import io.github.gear4jtest.core.processor.ProcessorResult;

public class OperationParamsInjector implements PreProcessor<List<ParameterModel<?, ?>>> {

	@Override
	public ProcessorResult process(Item input, List<ParameterModel<?, ?>> model, ProcessorDriver chain,
			StepExecution context) {
		if (model == null || model.isEmpty()) {
			return chain.proceed();
		}
//		throw new RuntimeException();
		
		Iterator<ParameterModel<?, ?>> it = model.iterator();
		while (it.hasNext()) {
			OperationModel.ParameterModel param = it.next();

			Parameter parameterValue = param.getParamRetriever().getParameterValue(context.getOperation());
			if (parameterValue == null) {
				
			}
			parameterValue.setValue(param.getValue());
		}
		return chain.proceed();
	}

	public static class Parameter<T> {

		private T value;

		private Parameter() {
		}

		private Parameter(T value) {
			this.value = value;
		}

		public static <T> Parameter<T> of(T value) {
			return new Parameter<T>(value);
		}

		public static <T> Parameter<T> of() {
			return new Parameter<>();
		}

		public T getValue() {
			return value;
		}

		void setValue(T value) {
			this.value = value;
		}

	}

}
