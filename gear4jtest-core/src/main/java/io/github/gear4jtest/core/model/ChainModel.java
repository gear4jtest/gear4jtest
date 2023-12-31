package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;
import io.github.gear4jtest.core.processor.Transformer;

public class ChainModel<IN, OUT> {

	private ResourceFactory resourceFactory;
	private BaseLineModel startingElement;
	private ChainDefaultConfiguration chainDefaultConfiguration;
	private EventHandlingDefinition eventHandling;

	private ChainModel() {
	}

	public ResourceFactory getResourceFactory() {
		return resourceFactory;
	}

	public BaseLineModel<IN, OUT> getStartingElement() {
		return startingElement;
	}

	public ChainDefaultConfiguration getChainDefaultConfiguration() {
		return chainDefaultConfiguration;
	}

	public EventHandlingDefinition getEventHandling() {
		return eventHandling;
	}

	public static class ChainDefaultConfiguration {

		private StepLineElementDefaultConfiguration stepLineElementDefaultConfiguration;

		public StepLineElementDefaultConfiguration getStepLineElementDefaultConfiguration() {
			return stepLineElementDefaultConfiguration;
		}

		public static class Builder {

			private final ChainDefaultConfiguration managedInstance;

			Builder() {
				managedInstance = new ChainDefaultConfiguration();
			}

			public Builder stepDefaultConfiguration(
					StepLineElementDefaultConfiguration stepLineElementDefaultConfiguration) {
				this.managedInstance.stepLineElementDefaultConfiguration = stepLineElementDefaultConfiguration;
				return this;
			}

			public ChainDefaultConfiguration build() {
				return managedInstance;
			}

		}

	}

	public static class StepLineElementDefaultConfiguration {

		private List<Class<? extends ProcessingOperationProcessor>> preProcessors;
		private List<Class<? extends ProcessingOperationProcessor>> postProcessors;
		private List<BaseOnError> onErrors;
		private Transformer transformer;

		private StepLineElementDefaultConfiguration() {
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

			private final StepLineElementDefaultConfiguration managedInstance;

			Builder() {
				managedInstance = new StepLineElementDefaultConfiguration();
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

			public UnsafeStepLineElementDefaultConfiguration.Builder onError(UnsafeOnError<?> onError) {
				this.managedInstance.onErrors.add(onError.getOnError());
				return new UnsafeStepLineElementDefaultConfiguration.Builder(this);
			}

			public StepLineElementDefaultConfiguration build() {
				return managedInstance;
			}
		}

	}
	
	public static class UnsafeStepLineElementDefaultConfiguration {

		private StepLineElementDefaultConfiguration.Builder configurationBuilder;

		public static class Builder {

			private UnsafeStepLineElementDefaultConfiguration managedInstance;

			public Builder(StepLineElementDefaultConfiguration.Builder configuration) {
				this.managedInstance = new UnsafeStepLineElementDefaultConfiguration();
				this.managedInstance.configurationBuilder = configuration;
			}

			public Builder onError(BaseOnError onError) {
				this.managedInstance.configurationBuilder.onError(onError);
				return this;
			}

			public UnsafeStepLineElementDefaultConfiguration.Builder onError(UnsafeOnError<?> onError) {
				this.managedInstance.configurationBuilder.onError(onError.getOnError());
				return this;
			}

			public StepLineElementDefaultConfiguration.Builder transformer(Transformer transformer) {
				this.managedInstance.configurationBuilder.transformer(transformer);
				return this.managedInstance.configurationBuilder;
			}

		}

	}

	public static class Builder<IN, OUT> {

		private final ChainModel<IN, OUT> managedInstance;

		Builder() {
			managedInstance = new ChainModel<>();
		}

		public Builder<IN, OUT> resourceFactory(ResourceFactory resourceFactory) {
			managedInstance.resourceFactory = resourceFactory;
			return this;
		}

		public <BEGIN, END> Builder<BEGIN, END> assemble(BaseLineModel<BEGIN, END> startingElement) {
			managedInstance.startingElement = startingElement;
			return (Builder<BEGIN, END>) this;
		}
		
		public Builder<IN, OUT> eventHandling(EventHandlingDefinition eventHandling) {
			managedInstance.eventHandling = eventHandling;
			return this;
		}

		public Builder<IN, OUT> defaultConfiguration(ChainDefaultConfiguration chainDefaultConfiguration) {
			managedInstance.chainDefaultConfiguration = chainDefaultConfiguration;
			return this;
		}

		public ChainModel<IN, OUT> build() {
			return managedInstance;
		}

	}

}
