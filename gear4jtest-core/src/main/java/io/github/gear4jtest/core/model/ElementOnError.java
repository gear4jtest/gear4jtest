package io.github.gear4jtest.core.model;

import java.util.List;

import io.github.gear4jtest.core.processor.BaseProcessor;

public class ElementOnError extends OnError {

	private RuleOverridingPolicy policy;
	
	private ElementOnError() {
		this.policy = RuleOverridingPolicy.APPEND;
	}
	
	public RuleOverridingPolicy getPolicy() {
		return policy;
	}

	public static class Builder {

		private final ElementOnError managedInstance;

		Builder() {
			managedInstance = new ElementOnError();
		}

		public ElementOnError.Builder policy(RuleOverridingPolicy policy) {
			managedInstance.policy = policy;
			return this;
		}
		
		public ElementOnError.Builder processor(Class<? extends BaseProcessor<?, ?, ?>> processor) {
			managedInstance.processor = processor;
			return this;
		}

		public ElementOnError.Builder rules(List<Rule> rules) {
			managedInstance.rules.addAll(rules);
			return this;
		}
		
		public ElementOnError.Builder rule(Rule rule) {
			managedInstance.rules.add(rule);
			return this;
		}

		public ElementOnError build() {
			return managedInstance;
		}

	}
	
	public static enum RuleOverridingPolicy {
		
		OVERRIDE, APPEND;
		
	}

}