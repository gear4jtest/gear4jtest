package io.github.gear4jtest.core.model.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Step;

public class Stepa<IN, OUT> {
	
	private Supplier<Step<IN, OUT>> operation;
	
	private List<Parameter> parameters;

	private Stepa() {
		this.parameters = new ArrayList<>();
	}
	
	public Supplier<Step<IN, OUT>> getOperation() {
		return operation;
	}
	
	public List<Parameter> getParameters() {
		return parameters;
	}

	public static class Builder<IN, OUT> {

		private final Stepa<IN, OUT> managedInstance;
		
		Builder() {
			managedInstance = new Stepa<>();
		}
		
		public <A> Builder<IN, A> operation(Supplier<Step<IN, A>> operation) {
			managedInstance.operation = (Supplier) operation;
			return (Builder<IN, A>) this;
		}
		
		public Builder<IN, OUT> parameter(String name, String evaluator, String value) {
			managedInstance.parameters.add(new Parameter(name, value, evaluator));
			return this;
		}

		public Stepa<IN, OUT> build() {
			return managedInstance;
		}

	}
	
	public static class Parameter {
		
		private String name;
		private String value;
		private String evaluator;

		public Parameter(String name, String value, String evaluator) {
			this.name = name;
			this.value = value;
			this.evaluator = evaluator;
		}

	}
	
}