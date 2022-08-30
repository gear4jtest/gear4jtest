package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.List;

public class LineElement {

	private List<LineElement> nextLineElement = new ArrayList<>();
	
	public List<LineElement> getNextLineElement() {
		return nextLineElement;
	}
	
}
