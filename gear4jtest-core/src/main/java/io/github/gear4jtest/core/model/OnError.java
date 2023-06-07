package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.processor.BaseProcessor;

public class OnError {

	protected Class<? extends BaseProcessor<?, ?>> processor;

	protected List<Rule> rules;

	OnError() {
		this.rules = new ArrayList<>();
	}

	public Class<? extends BaseProcessor<?, ?>> getProcessor() {
		return processor;
	}

	public List<Rule> getRules() {
		return rules;
	}

	public static class Builder {

		protected final OnError managedInstance;

		Builder() {
			managedInstance = new OnError();
		}

		public OnError.Builder processor(Class<? extends BaseProcessor<?, ?>> processor) {
			managedInstance.processor = processor;
			return this;
		}

		public OnError.Builder rules(List<Rule> rules) {
			managedInstance.rules.addAll(rules);
			return this;
		}

		public OnError build() {
			return managedInstance;
		}

	}
	
	public static class ProcessingBuilder {

		protected final OnError managedInstance;

		ProcessingBuilder() {
			managedInstance = new OnError();
		}
		
		public OnError.ProcessingBuilder processor(Class<? extends BaseProcessor<?, ?>> processor) {
			managedInstance.processor = processor;
			return this;
		}

		public UnsafeOnError.Builder ignore() {
			managedInstance.rules.add(new Rule.Builder().ignore().build());
			return new UnsafeOnError.Builder();
		}

		public OnError build() {
			return managedInstance;
		}

	}
	
	public static class UnsafeOnError {
		
		public static class Builder {

			public UnsafeOnError.Builder processor(Class<? extends BaseProcessor<?, ?>> processor) {
//				managedInstance.processor = processor;
				return this;
			}

			public UnsafeOnError.Builder rules(List<Rule> rules) {
//				managedInstance.rules.addAll(rules);
				return this;
			}

			public UnsafeOnError build() {
				return new UnsafeOnError();
			}

		}
		
	}

}