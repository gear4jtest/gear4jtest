package io.github.gear4jtest.core.model;

public class AssemblyLine<BEGIN, OUT> {

	private LineElement startingElement;
	
	public AssemblyLine(LineElement startingElement) {
		this.startingElement = startingElement;
	}
	
	public LineElement getStartingElement() {
		return startingElement;
	}
	
}
