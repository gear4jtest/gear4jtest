package io.github.gear4jtest.core.internal;

import java.util.List;

import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.processor.BaseProcessor;

public class ProcessorInternalModel {

	private Class<? extends BaseProcessor<?, ?>> processor;

	private List<BaseOnError> onErrors;

	public ProcessorInternalModel(Class<? extends BaseProcessor<?, ?>> processor, List<BaseOnError> onErrors) {
		this.processor = processor;
		this.onErrors = onErrors;
//		Object a = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}

	public Class<? extends BaseProcessor<?, ?>> getProcessor() {
		return processor;
	}

	public List<BaseOnError> getOnErrors() {
		return onErrors;
	}

}
