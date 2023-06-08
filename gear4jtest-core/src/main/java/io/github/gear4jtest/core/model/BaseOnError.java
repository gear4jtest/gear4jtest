package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.processor.BaseProcessor;

public class BaseOnError {

	protected Class<? extends BaseProcessor<?, ?>> processor;

	protected List<BaseRule> rules;

	BaseOnError() {
		this.rules = new ArrayList<>();
	}
	
	public boolean isGlobal() {
		return this.processor == null;
	}

	public Class<? extends BaseProcessor<?, ?>> getProcessor() {
		return processor;
	}

	public List<BaseRule> getRules() {
		return rules;
	}

}