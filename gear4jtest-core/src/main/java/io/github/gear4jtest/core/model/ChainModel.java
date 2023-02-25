package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.internal.StepLineElement;
import io.github.gear4jtest.core.processor.BaseProcessor;

public class ChainModel<IN, OUT> {

	private BranchesModel branches;
	private ChainDefaultConfiguration chainDefaultConfiguration;
	
	private ChainModel() {
	}
	
	public BranchesModel getBranches() {
		return branches;
	}
	
	public ChainDefaultConfiguration getChainDefaultConfiguration() {
		return chainDefaultConfiguration;
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
			
			public Builder stepDefaultConfiguration(StepLineElementDefaultConfiguration stepLineElementDefaultConfiguration) {
				this.managedInstance.stepLineElementDefaultConfiguration = stepLineElementDefaultConfiguration;
				return this;
			}
			
			public ChainDefaultConfiguration build() {
				return managedInstance;
			}
			
		}
		
	}
	
	public static class StepLineElementDefaultConfiguration {
		
		private List<ProcessorModel<StepLineElement>> preProcessors;
		private List<ProcessorModel<StepLineElement>> postProcessors;
		private List<OnError> onErrors;
		
		private StepLineElementDefaultConfiguration() {
			this.preProcessors = new ArrayList<>();
			this.postProcessors = new ArrayList<>();
		}
		
		public List<ProcessorModel<StepLineElement>> getPreProcessors() {
			return preProcessors;
		}
		
		public List<ProcessorModel<StepLineElement>> getPostProcessors() {
			return postProcessors;
		}
		
		public static class Builder {

			private final StepLineElementDefaultConfiguration managedInstance;
			
			Builder() {
				managedInstance = new StepLineElementDefaultConfiguration();
			}
			
			public Builder preProcessor(ProcessorModel<StepLineElement> processor) {
				this.managedInstance.preProcessors.add(processor);
				return this;
			}

			public Builder postProcessor(ProcessorModel<StepLineElement> processor) {
				this.managedInstance.postProcessors.add(processor);
				return this;
			}
			
			public Builder onError(OnError onError) {
				this.managedInstance.onErrors.add(onError);
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
		
		public <A> Builder<IN, A> assemble(BranchesModel<IN, A> branches) {
			managedInstance.branches = branches;
			return (Builder<IN, A>) this;
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
