package io.github.gear4jtest.core.service;

import java.util.Optional;

import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.LineElement;
import io.github.gear4jtest.core.model.simple.Branch;
import io.github.gear4jtest.core.model.simple.Branches;
import io.github.gear4jtest.core.model.simple.Chain;
import io.github.gear4jtest.core.model.simple.Stepa;

public class ChainerService {

	public <BEGIN, IN> AssemblyLine<BEGIN, IN> buildChain(Chain<BEGIN, IN> chain) {
		LineElement startingElement = buildLineElement(chain.getBranches(), null);
		return new AssemblyLine<>(startingElement);
	}

	private static LineElement buildLineElement(Branches<?, ?> branches, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(branches);
		branches.getBranches().forEach(a -> buildLineElement(a, element));
		Optional.ofNullable(parentElement).ifPresent(a -> a.getNextLineElement().add(element));
		return element;
	}

	private static LineElement buildLineElement(Branch<?, ?> branch, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(branch);
		parentElement.getNextLineElement().add(element);
		LineElement parent = element;
		for (Object child : branch.getChildren()) {
			LineElement elem = buildLineElement(child, parent);
			parent = elem;
		}
		return element;
	}

	private static LineElement buildLineElement(Stepa<?, ?> stepa, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(stepa);
		parentElement.getNextLineElement().add(element);
		return element;
	}
	
	private static LineElement buildLineElement(Object object, LineElement parentElement) {
		if (object instanceof Branches) {
			return buildLineElement((Branches) object, parentElement);
		} else if (object instanceof Branch) {
			return buildLineElement((Branch) object, parentElement);
		} else if (object instanceof Stepa) {
			return buildLineElement((Stepa) object, parentElement);
		} else {
			throw new IllegalArgumentException();
		}
	}

}
