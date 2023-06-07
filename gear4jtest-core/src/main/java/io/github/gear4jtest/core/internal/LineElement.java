package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;

public abstract class LineElement {

	private final UUID id;
	private final List<LineElement> nextLineElements;
//	private final LineElement parentElement; // Should this be stored here as the only use is to contextualize events so that it is tied to parent ?

	// Should these methods be parts of interfaces ? LineExecutor, LineModel... ?
	public abstract Item execute(Item item);
	public abstract LineElementExecution createLineElementExecution(ItemExecution itemExecution);
//  public abstract Model getLineModel();

	public LineElement(/*LineElement parentElement*/) {
		this.id = UUID.randomUUID();
		this.nextLineElements = new ArrayList<>();
//		this.parentElement = parentElement;
	}

	public UUID getId() {
		return id;
	}

	public void addNextLineElement(LineElement nextElement) {
		nextLineElements.add(nextElement);
	}

	public List<LineElement> getNextLineElements() {
		return new ArrayList<>(nextLineElements);
	}

//	public LineElement getParentElement() {
//		return parentElement;
//	}

}
