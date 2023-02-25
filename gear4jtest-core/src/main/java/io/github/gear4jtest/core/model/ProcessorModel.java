package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.Processor;

public class ProcessorModel<T extends LineElement> {

	private Processor<T> processor;

	private List<OnError> onErrors;

	private ProcessorModel() {
		onErrors = new ArrayList<>();
	}

	public Processor<T> getProcessor() {
		return processor;
	}

	public List<OnError> getOnErrors() {
		return onErrors;
	}

	public static class Builder<T extends LineElement> {

		private final ProcessorModel<T> managedInstance;

		Builder() {
			managedInstance = new ProcessorModel<>();
		}

		public Builder<T> processor(Processor<T> processor) {
			managedInstance.processor = processor;
			return this;
		}

		public Builder<T> onError(OnError onError) {
			managedInstance.onErrors.add(onError);
			return this;
		}

		public ProcessorModel<T> build() {
			return managedInstance;
		}

	}

}
