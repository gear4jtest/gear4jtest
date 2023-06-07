package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.OnError.UnsafeOnError;
import io.github.gear4jtest.core.processor.Invoker;
import io.github.gear4jtest.core.processor.PostProcessor;
import io.github.gear4jtest.core.processor.PreProcessor;
import io.github.gear4jtest.core.processor.operation.OperationInvoker;

public class ChainModel<IN, OUT> {

	private ResourceFactory resourceFactory;
	private BranchesModel branches;
	private ChainDefaultConfiguration chainDefaultConfiguration;
	private EventHandling eventHandling;

	private ChainModel() {
	}

	public ResourceFactory getResourceFactory() {
		return resourceFactory;
	}

	public BranchesModel getBranches() {
		return branches;
	}

	public ChainDefaultConfiguration getChainDefaultConfiguration() {
		return chainDefaultConfiguration;
	}

	public EventHandling getEventHandling() {
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

		private List<Class<? extends PreProcessor>> preProcessors;
		private List<Class<? extends PostProcessor>> postProcessors;
		private Class<? extends Invoker> invoker;
		private List<OnError> onErrors;

		private StepLineElementDefaultConfiguration() {
			this.preProcessors = new ArrayList<>();
			this.postProcessors = new ArrayList<>();
			this.invoker = OperationInvoker.class;
			this.onErrors = new ArrayList<>();
		}

		public List<Class<? extends PreProcessor>> getPreProcessors() {
			return preProcessors;
		}

		public List<Class<? extends PostProcessor>> getPostProcessors() {
			return postProcessors;
		}

		public Class<? extends Invoker> getInvoker() {
			return invoker;
		}

		public List<OnError> getOnErrors() {
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

			public Builder preProcessors(List<Class<? extends PreProcessor>> list) {
				this.managedInstance.preProcessors.addAll(list);
				return this;
			}

			public Builder invoker(Class<? extends Invoker> invoker) {
				this.managedInstance.invoker = invoker;
				return this;
			}

//			public Builder postProcessor(ProcessorModel<StepLineElement> processor) {
//				this.managedInstance.postProcessors.add(processor);
//				return this;
//			}

			public Builder postProcessors(List<Class<? extends PostProcessor>> processors) {
				this.managedInstance.postProcessors.addAll(processors);
				return this;
			}

			public <T extends OnError> Builder onError(T onError) {
				this.managedInstance.onErrors.add(onError);
				return this;
			}

			public Builder onError(UnsafeOnError onError) {
//				this.managedInstance.onErrors.add(onError);
				return this;
			}

			public StepLineElementDefaultConfiguration build() {
				return managedInstance;
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

		public <A> Builder<IN, A> assemble(BranchesModel<IN, A> branches) {
			managedInstance.branches = branches;
			return (Builder<IN, A>) this;
		}
		
		public Builder<IN, OUT> eventHandling(EventHandling eventHandling) {
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
