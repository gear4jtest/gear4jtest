package io.github.gear4jtest.core.internal;

public class Item {

	private Object item;

	public Item(Object item) {
		this.item = item;
	}

	public void updateItem(Object item) {
		this.item = item;
	}

	public Object getItem() {
		return item;
	}

}
