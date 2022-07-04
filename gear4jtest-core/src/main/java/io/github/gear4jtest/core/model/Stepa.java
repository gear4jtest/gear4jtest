package io.github.gear4jtest.core.model;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class Stepa {

	private Supplier<Step> step;
	
	private Stepa() {
	}
	
	public static <CHAININ, BRANCHESIN, STEPIN> ChainBuilder<CHAININ, BRANCHESIN, STEPIN, ?> newStep(
			Branch.ChainBuilder<CHAININ, BRANCHESIN, STEPIN> parentBuilder, 
			Consumer<Stepa> callback) {
		return new ChainBuilder<>(parentBuilder, callback);
	}

	public static <A, B, C, D, E> Builder2<A, B, C, D, E, ?> newStep(
			Branch.Builder2<A, B, C, D, E> parentBuilder, 
			Consumer<Stepa> callback) {
		return new Builder2<>(parentBuilder, callback);
	}
	
	public static class ChainBuilder<CHAININ, BRANCHESIN, STEPIN, OUT> {

		private Stepa managedInstance = new Stepa();
		
		private Consumer<Stepa> callback;
		
		private Branch.ChainBuilder<CHAININ, BRANCHESIN, STEPIN> parentBuilder;
		
		private ChainBuilder(Branch.ChainBuilder<CHAININ, BRANCHESIN, STEPIN> parentBuilder, Consumer<Stepa> callback) {
			this.parentBuilder = parentBuilder;
		}

		public <A> ChainBuilder<CHAININ, BRANCHESIN, STEPIN, A> operation(Supplier<Step<STEPIN, A>> step) {
			managedInstance.step = (Supplier) step;
			return (ChainBuilder<CHAININ, BRANCHESIN, STEPIN, A>) this;
		}
		
		public <A> ChainBuilder<CHAININ, BRANCHESIN, STEPIN, A> returns(String expression, Class<A> clazz) {
			return (ChainBuilder<CHAININ, BRANCHESIN, STEPIN, A>) this;
		}
		
		public <A> Branch.ChainBuilder<CHAININ, BRANCHESIN, OUT> done() {
			callback.accept(managedInstance);
			return (Branch.ChainBuilder<CHAININ, BRANCHESIN, OUT>) parentBuilder;
		}

	}
	
	public static class Builder2<CHAININ, BRANCHESIN, PARENTIN, PARENTIN2, STEPIN, OUT> {

		private Stepa managedInstance = new Stepa();
		
		private Consumer<Stepa> callback;
		
		private Branch.Builder2<CHAININ, BRANCHESIN, PARENTIN, PARENTIN2, STEPIN> parentBuilder;
		
		private Builder2(Branch.Builder2<CHAININ, BRANCHESIN, PARENTIN, PARENTIN2, STEPIN> parentBuilder, Consumer<Stepa> callback) {
			this.parentBuilder = parentBuilder;
		}

		public <A> Builder2<CHAININ, BRANCHESIN,  PARENTIN, PARENTIN2, STEPIN, A> operation(Supplier<Step<STEPIN, A>> step) {
			managedInstance.step = (Supplier) step;
			return (Builder2<CHAININ, BRANCHESIN,  PARENTIN, PARENTIN2, STEPIN, A>) this;
		}
		
		public <A> Builder2<CHAININ, BRANCHESIN,  PARENTIN, PARENTIN2, STEPIN, A> returns(String expression, Class<A> clazz) {
			return (Builder2<CHAININ, BRANCHESIN,  PARENTIN, PARENTIN2, STEPIN, A>) this;
		}
		
		public <A> Branch.Builder2<CHAININ, BRANCHESIN, PARENTIN, PARENTIN2, OUT> done() {
			callback.accept(managedInstance);
			return (Branch.Builder2<CHAININ, BRANCHESIN, PARENTIN, PARENTIN2, OUT>) parentBuilder;
		}
		
	}
	
}
