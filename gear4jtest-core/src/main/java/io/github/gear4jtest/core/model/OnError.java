package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.processor.BaseProcessor;

public class OnError {

	private Class<? extends Throwable> type;
	
	private Class<? extends BaseProcessor<?, ?>> processor;
	
	private boolean ignore;
	
	private boolean processorChainFatal;

	private boolean fatal;
	
	private OnError() {
		this.fatal = true;
	}

	public Class<? extends Throwable> getType() {
		return type;
	}
	
	public Class<? extends BaseProcessor<?, ?>> getProcessor() {
		return processor;
	}

	public boolean isIgnore() {
		return ignore;
	}

	public boolean isProcessorChainFatal() {
		return processorChainFatal;
	}

	public boolean isFatal() {
		return fatal;
	}
	
	public static class Builder {

		private final OnError managedInstance;

		Builder() {
			managedInstance = new OnError();
		}

		public OnError.Builder type(Class<? extends Throwable> clazz) {
			managedInstance.type = clazz;
			return this;
		}
		
		public OnError.Builder processor(Class<? extends BaseProcessor<?, ?>> processor) {
			managedInstance.processor = processor;
			return this;
		}
		
		public OnError.Builder ignore() {
			managedInstance.ignore = true;
			managedInstance.fatal = false;
			managedInstance.processorChainFatal = false;
			return this;
		}
		
		public OnError.Builder processorChainFatal(boolean processorChainFatal) {
			managedInstance.processorChainFatal = processorChainFatal;
			return this;
		}
		
		public OnError.Builder fatal(boolean fatal) {
			managedInstance.fatal = fatal;
			return this;
		}
		
		public OnError build() {
			return managedInstance;
		}

	}

}