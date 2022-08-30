package io.github.gear4jtest.core.service;

import java.util.Optional;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.BranchLineElement;
import io.github.gear4jtest.core.model.BranchesLineElement;
import io.github.gear4jtest.core.model.LineElement;
import io.github.gear4jtest.core.model.StepLineElement;
import io.github.gear4jtest.core.model.simple.Branch;
import io.github.gear4jtest.core.model.simple.Branches;
import io.github.gear4jtest.core.model.simple.Chain;
import io.github.gear4jtest.core.model.simple.Stepa;

public class ChainerService {
	
	public <BEGIN, IN> AssemblyLine<BEGIN, IN> execute(Chain<BEGIN, IN> chain) {
		LineElement startingElement = buildLineElement(chain.getBranches(), null);
		return new AssemblyLine<>(startingElement);
	}
	
	private LineElement buildLineElement(Object object, LineElement parentElement) {
		if (object instanceof Branches) {
			BranchesLineElement element = (BranchesLineElement) buildLineElement((Branches) object);
			((Branches<?, ?>) object).getBranches().stream()
					.map(a -> buildLineElement(a, element))
					.collect(Collectors.toList());
			Optional.ofNullable(parentElement).ifPresent(a -> a.getNextLineElement().add(element));
			return element;
		} else if (object instanceof Branch) {
			BranchLineElement element = (BranchLineElement) buildLineElement((Branch) object);
			parentElement.getNextLineElement().add(element);
			LineElement parent = element;
			for (Object child : ((Branch<?, ?>) object).getChildren()) {
				LineElement elem = buildLineElement(child, parent);
				parent = elem;
			}
			return element;
		} else {
			StepLineElement element = (StepLineElement) buildLineElement((Stepa) object);
			parentElement.getNextLineElement().add(element);
			return element;
		}
	}
	
	private LineElement buildLineElement(Branches branches) {
		return new BranchesLineElement(branches);
	}
	
	private LineElement buildLineElement(Branch branch) {
		return new BranchLineElement(branch);
	}
	
	private LineElement buildLineElement(Stepa step) {
		return new StepLineElement(step);
	}
	
}
