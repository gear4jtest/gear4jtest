package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.ItemExecution;

public class Item {

	private Object item;

	private ItemExecution execution;

	public Item(Object item, ItemExecution execution) {
		this.item = item;
		this.execution = execution;
	}

	public Item withItem(Object item) {
		return new Item(item, execution);
	}

	public Object getItem() {
		return item;
	}

	public ItemExecution getExecution() {
		return execution;
	}

}
