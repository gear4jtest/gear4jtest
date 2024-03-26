package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.model.BaseOnError;
import io.github.gear4jtest.core.model.UnsafeOnError;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;
import io.github.gear4jtest.core.processor.Transformer;

public class OperationConfigurationDefinition {

	private List<Class<? extends ProcessingOperationProcessor>> preProcessors;
	private List<Class<? extends ProcessingOperationProcessor>> postProcessors;
	private List<BaseOnError> onErrors;
	private Transformer transformer;

	private OperationConfigurationDefinition() {
		this.preProcessors = new ArrayList<>();
		this.postProcessors = new ArrayList<>();
		this.onErrors = new ArrayList<>();
	}

	public List<Class<? extends ProcessingOperationProcessor>> getPreProcessors() {
		return preProcessors;
	}

	public List<Class<? extends ProcessingOperationProcessor>> getPostProcessors() {
		return postProcessors;
	}

	public List<BaseOnError> getOnErrors() {
		return onErrors;
	}

	public static class Builder {

		private final OperationConfigurationDefinition managedInstance;

		public Builder() {
			managedInstance = new OperationConfigurationDefinition();
		}

//			public Builder preProcessor(ProcessorModel<StepLineElement> processor) {
//				this.managedInstance.preProcessors.add(processor);
//				return this;
//			}

//			public Builder preProcessors(List<ProcessorModel<StepLineElement>> processors) {
//				this.managedInstance.preProcessors.addAll(processors);
//				return this;
//			}

		public Builder preProcessors(List<Class<? extends ProcessingOperationProcessor>> list) {
			this.managedInstance.preProcessors.addAll(list);
			return this;
		}

//			public Builder postProcessor(ProcessorModel<StepLineElement> processor) {
//				this.managedInstance.postProcessors.add(processor);
//				return this;
//			}

		public Builder postProcessors(List<Class<? extends ProcessingOperationProcessor>> processors) {
			this.managedInstance.postProcessors.addAll(processors);
			return this;
		}

		public Builder transformer(Transformer transformer) {
			this.managedInstance.transformer = transformer;
			return this;
		}

		public Builder onError(BaseOnError onError) {
			this.managedInstance.onErrors.add(onError);
			return this;
		}

		public UnsafeStepLineElementDefaultConfigurationDefinition.Builder onError(UnsafeOnError<?> onError) {
			this.managedInstance.onErrors.add(onError.getOnError());
			return new UnsafeStepLineElementDefaultConfigurationDefinition.Builder(this);
		}

		public OperationConfigurationDefinition build() {
			return managedInstance;
		}
	}

}