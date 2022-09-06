package io.github.gear4jtest.core.model.simple;

import java.util.List;
import java.util.function.Supplier;

import io.github.gear4jtest.core.model.Step;

public final class ElementBuilders {
	
	private ElementBuilders() {}
	
	public static <A> Chain.Builder<A, ?> chain() {
		return new Chain.Builder<>();
	}
	
	public static <A> Chain.Builder<A, ?> chain(Class<A> clazz) {
		return new Chain.Builder<>();
	}
	
	public static <A> Stepa.Builder<A, ?> step() {
		return new Stepa.Builder<>();
	}
	
	public static <A, B> Stepa.Builder<A, B> step(Supplier<Step<A, B>> step) {
		return new Stepa.Builder<A, B>()
				.operation(step);
	}
	
	public static <A> Branches.Builder<A, List<Object>> branches() {
		return new Branches.Builder<>();
	}
	
	public static <A> Branches.Builder<A, List<Object>> branches(Class<A> clazz) {
		return new Branches.Builder<>();
	}
	
	public static <A> Branch.Builder<A, A> branch() {
		return new Branch.Builder<>();
	}
	
	public static <A> Branch.Builder<A, A> branch(Class<A> clazz) {
		return new Branch.Builder<>();
	}
	
}
