package io.github.gear4jtest.core.context;

public class Contexts {

	private Gear4jContext globalContext;

	private ItemContext itemContext;

	private LineElementContext lineElementContext;

	public Gear4jContext getGlobalContext() {
		return globalContext;
	}

	public void setGlobalContext(Gear4jContext globalContext) {
		this.globalContext = globalContext;
	}

	public ItemContext getItemContext() {
		return itemContext;
	}

	public void setItemContext(ItemContext itemContext) {
		this.itemContext = itemContext;
	}

	public LineElementContext getLineElementContext() {
		return lineElementContext;
	}

	public void setLineElementContext(LineElementContext lineElementContext) {
		this.lineElementContext = lineElementContext;
	}

}
