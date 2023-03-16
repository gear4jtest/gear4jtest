package io.github.gear4jtest.core.internal;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.processor.Processor;

public class ProcessorInternalModel<T extends LineElement> {

	private Supplier<? extends Processor<T>> processor;

	private List<BaseOnError> onErrors;

	public ProcessorInternalModel(Supplier<? extends Processor<T>> processor, List<BaseOnError> onErrors) {
		this.processor = processor;
		this.onErrors = onErrors;
//		Object a = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}

	public Supplier<? extends Processor<T>> getProcessor() {
		return processor;
	}

	public List<BaseOnError> getOnErrors() {
		return onErrors;
	}

}
