package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;

public class LineElement {

	private AssemblyLine line;

	private List<LineElement> nextLineElements = new ArrayList<>();
	
	public LineElement(AssemblyLine line) {
		this.line = line;
	}

	public void addNextLineElement(LineElement nextElement) {
		nextLineElements.add(nextElement);
	}

	public AssemblyLine getLine() {
		return line;
	}

	public List<LineElement> getNextLineElements() {
		return new ArrayList<>(nextLineElements);
	}

}
