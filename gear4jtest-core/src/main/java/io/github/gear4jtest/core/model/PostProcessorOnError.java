package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.processor.ProcessingOperationProcessor;

public class PostProcessorOnError extends BaseOnError {

	PostProcessorOnError() {
		super();
	}

	public static class Builder {

		protected final PostProcessorOnError managedInstance;

		Builder() {
			managedInstance = new PostProcessorOnError();
		}

		public PostProcessorOnError.Builder processor(Class<? extends ProcessingOperationProcessor<?>> processor) {
			managedInstance.processor = processor;
			return this;
		}
		
		public <X extends BaseRule> PostProcessorOnError.Builder rule(X rule) {
			managedInstance.rules.add(rule);
			return this;
		}

		public PostProcessorOnError build() {
			return managedInstance;
		}

	}
	
//	public static class UnsafeOnError {
//		
//		private PostProcessorOnError onError;
//		
//		public UnsafeOnError(PostProcessorOnError onError) {
//			this.onError = onError;
//		}
//		
//		public PostProcessorOnError getOnError() {
//			return onError;
//		}
//
//		public static class Builder {
//			
//			private final UnsafeOnError managedInstance;
//
//			Builder(PostProcessorOnError onError) {
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