package io.github.gear4jtest.core.internal;

public class Item {

	private Object item;

	public Item(Object item) {
		this.item = item;
	}

	public Item withItem(Object item) {
		return new Item(item);
	}

	public Object getItem() {
		return item;
	}

}
