package io.github.gear4jtest.core.model;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Stepa<BEGIN, BRANCHESIN, STEPIN, OUT> {

	private Supplier<Step<STEPIN, OUT>> step;
	
	private Stepa() {
	}

	public static <CHAININ, BRANCHESIN, STEPIN> Builder<CHAININ, BRANCHESIN, STEPIN, ?> newStep(
			Branch.Builder<CHAININ, BRANCHESIN, STEPIN> parentBuilder, 
			Consumer<Stepa<CHAININ, BRANCHESIN, STEPIN, ?>> callback) {
		return new Builder<>(parentBuilder, callback);
	}
	
	public static class Builder<CHAININ, BRANCHESIN, STEPIN, OUT> {

		private Stepa<CHAININ, BRANCHESIN, STEPIN, OUT> managedInstance = new Stepa<>();
		
		private Consumer<Stepa<CHAININ, BRANCHESIN, STEPIN, ?>> callback;
		
		private Branch.Builder<CHAININ, BRANCHESIN, STEPIN> parentBuilder;
		
		private Builder(Branch.Builder<CHAININ, BRANCHESIN, STEPIN> parentBuilder, Consumer<Stepa<CHAININ, BRANCHESIN, STEPIN, ?>> callback) {
			this.parentBuilder = parentBuilder;
		}

		public <A> Builder<CHAININ, BRANCHESIN, STEPIN, A> operation(Supplier<Step<STEPIN, A>> step) {
			managedInstance.step = (Supplier) step;
			return (Builder<CHAININ, BRANCHESIN, STEPIN, A>) this;
		}
		
		public <A> Builder<CHAININ, BRANCHESIN, STEPIN, A> returns(String expression, Class<A> clazz) {
			return (Builder<CHAININ, BRANCHESIN, STEPIN, A>) this;
		}
		
		public <A> Branch.Builder<CHAININ, BRANCHESIN, OUT> done() {
			callback.accept(managedInstance);
			return (Branch.Builder<CHAININ, BRANCHESIN, OUT>) parentBuilder;
		}

		public Stepa<CHAININ, BRANCHESIN, STEPIN, OUT> build() {
			return managedInstance;
		}
		
	}
	
}
