package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;

public class LineElement {

	private List<LineElement> nextLineElements = new ArrayList<>();
	
	public void addNextLineElement(LineElement nextElement) {
		nextLineElements.add(nextElement);
	}
	
	public List<LineElement> getNextLineElements() {
		return new ArrayList<>(nextLineElements);
	}
	
}
