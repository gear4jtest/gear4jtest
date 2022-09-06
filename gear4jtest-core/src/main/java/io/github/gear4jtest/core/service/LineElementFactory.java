package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.model.BranchLineElement;
import io.github.gear4jtest.core.model.BranchesLineElement;
import io.github.gear4jtest.core.model.LineElement;
import io.github.gear4jtest.core.model.StepLineElement;
import io.github.gear4jtest.core.model.simple.Branch;
import io.github.gear4jtest.core.model.simple.Branches;
import io.github.gear4jtest.core.model.simple.Stepa;

public class LineElementFactory { 

	private LineElementFactory() {}
	
	static LineElement buildLineElement(Branches branches) {
		return new BranchesLineElement(branches);
	}
	
	static LineElement buildLineElement(Branch branch) {
		return new BranchLineElement(branch);
	}
	
	static LineElement buildLineElement(Stepa step) {
		return new StepLineElement(step);
	}
	
}
