package io.github.gear4jtest.core.internal;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.OnError;
import io.github.gear4jtest.core.processor.Processor;

public class ProcessorInternalModel<T extends LineElement> {

	private Supplier<Processor<T>> processor;

	private List<OnError> onErrors;

	public ProcessorInternalModel(Supplier<Processor<T>> processor, List<OnError> onErrors) {
		this.processor = processor;
		this.onErrors = onErrors;
//		Object a = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}

	public Supplier<Processor<T>> getProcessor() {
		return processor;
	}

	public List<OnError> getOnErrors() {
		return onErrors;
	}

}
