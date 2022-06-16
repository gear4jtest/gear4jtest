package io.github.gear4jtest.core.model;

import java.util.List;

public class Branches<BEGIN, IN> implements Element {

	private final List<Branch> branches;
	
	public Branches(List<Branch> branches) {
		this.branches = branches;
	}
	
}