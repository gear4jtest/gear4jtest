package io.github.gear4jtest.core.internal;

import java.util.List;

public class LineTraverser {
	
	LineTraverser() {
		
	}
	
	public List<LineElement> getNextElement(LineElement element) {
		return element.getNextLineElements();
	}

}
