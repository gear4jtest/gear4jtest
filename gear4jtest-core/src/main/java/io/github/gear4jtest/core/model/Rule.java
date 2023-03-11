package io.github.gear4jtest.core.model;

public class Rule {

	private Class<? extends Throwable> type;

	private boolean ignore;

	private boolean processorChainFatal;

	private boolean fatal;

	private Rule() {
		this.fatal = true;
	}

	public Class<? extends Throwable> getType() {
		return type;
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

		private final Rule managedInstance;

		Builder() {
			managedInstance = new Rule();
		}

		public Rule.Builder type(Class<? extends Throwable> clazz) {
			managedInstance.type = clazz;
			return this;
		}

		public Rule.Builder ignore() {
			managedInstance.ignore = true;
			managedInstance.fatal = false;
			managedInstance.processorChainFatal = false;
			return this;
		}

		public Rule.Builder processorChainFatal(boolean processorChainFatal) {
			managedInstance.processorChainFatal = processorChainFatal;
			return this;
		}

		public Rule.Builder fatal(boolean fatal) {
			managedInstance.fatal = fatal;
			return this;
		}

		public Rule build() {
			return managedInstance;
		}

	}
}

