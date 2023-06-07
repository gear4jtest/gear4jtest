package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.processor.PreProcessor;

public class PreProcessorOnError extends BaseOnError {

	PreProcessorOnError() {
		super();
	}

	public static class Builder {

		protected final PreProcessorOnError managedInstance;

		Builder() {
			managedInstance = new PreProcessorOnError();
		}

		public PreProcessorOnError.Builder processor(Class<? extends PreProcessor<?>> processor) {
			managedInstance.processor = processor;
			return this;
		}

		public UnsafeOnError.Builder<PreProcessorOnError> rule(ChainBreakRule rule) {
			managedInstance.rules.add(rule);
			return new UnsafeOnError.Builder<>(managedInstance);
		}
		
		public <X extends BaseRule> PreProcessorOnError.Builder rule(X rule) {
			managedInstance.rules.add(rule);
			return this;
		}

		public PreProcessorOnError build() {
			return managedInstance;
		}

	}
	
//	public static class UnsafeOnError {
//		
//		private PreProcessorOnError onError;
//		
//		public UnsafeOnError(PreProcessorOnError onError) {
//			this.onError = onError;
//		}
//		
//		public PreProcessorOnError getOnError() {
//			return onError;
//		}
//
//		public static class Builder {
//			
//			private final UnsafeOnError managedInstance;
//
//			Builder(PreProcessorOnError onError) {
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