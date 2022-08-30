//package io.github.gear4jtest.core.model.generics.full;
//
//import java.util.function.Consumer;
//
//public class Chain<IN, OUT> {
//
//	private Branches branches;
//	
//	private Chain() {
//	}
//	
//	public static <A> Builder<A, ?> newChain() {
//		return new Builder<>();
//	}
//	
//	public static class Builder<IN, OUT> {
//
//		private final Chain<IN, OUT> managedInstance; 
//		
//		private Builder() {
//			managedInstance = new Chain<>();
//		}
//		
//		public Branches.ChainBuilder<IN, IN> branches() {
//			Consumer<Branches> callback = obj -> managedInstance.branches = obj;
//			return Branches.<IN>newBranches(this, callback);
//		}
//
////		public Branches.SimpleBuilder<IN, IN> branches() {
////			Consumer<Branches> callback = obj -> managedInstance.branches = obj;
////			return Branches.<IN>newBranches(this, callback);
////		}
//		
//		public Chain<IN, OUT> build() {
//			return managedInstance;
//		}
//
//	}
//	
//}
