package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.processor.ProcessingProcessor;

public class ProcessingProcessorOnError extends BaseOnError {

	ProcessingProcessorOnError() {
		super();
	}

	public static class Builder {

		protected final ProcessingProcessorOnError managedInstance;

		Builder() {
			managedInstance = new ProcessingProcessorOnError();
		}

		public ProcessingProcessorOnError.Builder processor(Class<? extends ProcessingProcessor> processor) {
			managedInstance.processor = processor;
			return this;
		}

		public UnsafeOnError.Builder<ProcessingProcessorOnError> rule(ChainBreakRule rule) {
			managedInstance.rules.add(rule);
			return new UnsafeOnError.Builder<>(managedInstance);
		}
		
		public UnsafeOnError.Builder<ProcessingProcessorOnError> rule(IgnoreRule rule) {
			managedInstance.rules.add(rule);
			return new UnsafeOnError.Builder<>(managedInstance);
		}
		
		public <X extends BaseRule> ProcessingProcessorOnError.Builder rule(X rule) {
			managedInstance.rules.add(rule);
			return this;
		}

		public ProcessingProcessorOnError build() {
			return managedInstance;
		}

	}
	
//	public static class UnsafeOnError {
//		
//		private ProcessingProcessorOnError onError;
//		
//		public UnsafeOnError(ProcessingProcessorOnError onError) {
//			this.onError = onError;
//		}
//		
//		public ProcessingProcessorOnError getOnError() {
//			return onError;
//		}
//
//		public static class Builder {
//			
//			private final UnsafeOnError managedInstance;
//
//			Builder(ProcessingProcessorOnError onError) {
//				managedInstance = new UnsafeOnError(onError);
//			}
//
//			public UnsafeOnError.Builder rule(BaseRule rule) {
//				managedInstance.onError.rules.add(rule);
//				return this;
//			}
//
//			public UnsafeOnError build() {
//				return managedInstance;
//			}
//
//		}
//		
//	}

}