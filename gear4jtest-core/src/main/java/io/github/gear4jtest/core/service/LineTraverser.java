package io.github.gear4jtest.core.service;

import java.util.List;

import io.github.gear4jtest.core.model.LineElement;

public class LineTraverser {
	
	public List<LineElement> getNextElement(LineElement element) {
		return element.getNextLineElement();
	}

}
