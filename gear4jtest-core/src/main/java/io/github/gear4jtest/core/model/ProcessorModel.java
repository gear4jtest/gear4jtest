package io.github.gear4jtest.core.model;

import java.util.function.Supplier;

import io.github.gear4jtest.core.internal.LineElement;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;

public class ProcessorModel<T extends LineElement> {

	private Supplier<ProcessingOperationProcessor> processor;

//	private List<OnError> onErrors;

	private ProcessorModel() {
//		onErrors = new ArrayList<>();
	}

	public Supplier<ProcessingOperationProcessor> getProcessor() {
		return processor;
	}

//	public List<OnError> getOnErrors() {
//		return onErrors;
//	}

	public static class Builder<T extends LineElement> {

		private final ProcessorModel<T> managedInstance;

		Builder() {
			managedInstance = new ProcessorModel<>();
		}

		public Builder<T> processor(Supplier<ProcessingOperationProcessor> processor) {
			managedInstance.processor = processor;
			return this;
		}

//		public Builder<T> onError(OnError onError) {
//			managedInstance.onErrors.add(onError);
//			return this;
//		}

		public ProcessorModel<T> build() {
			return managedInstance;
		}

	}

}
