package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.BranchExecution;
import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.model.BranchModel;

public class BranchLineElement extends LineElement {

	public BranchLineElement(BranchModel branch) {
		super();
	}

	@Override
	public LineElementExecution createLineElementExecution(ItemExecution itemExecution) {
		return null;
	}

	@Override
	public Item execute(Item item) {
		BranchExecution branchExecution = item.getExecution().createBranchExecution();
		return item;
	}
	
}
