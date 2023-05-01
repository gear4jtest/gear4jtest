package io.github.gear4jtest.core.context;

public class Contexts<T extends LineElementContext> {

	private Gear4jContext globalContext;

	private ItemContext itemContext;

	private T lineElementContext;
	
	public Contexts<T> clone() {
		Contexts<T> newContexts = new Contexts<>();
		newContexts.setGlobalContext(globalContext.clone());
		newContexts.setItemContext(itemContext.clone());
		newContexts.setLineElementContext((T) lineElementContext.clone());
		return newContexts;
	}

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

	public T getLineElementContext() {
		return lineElementContext;
	}

	public void setLineElementContext(T lineElementContext) {
		this.lineElementContext = lineElementContext;
	}

}
