package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.context.BranchesExecution;
import io.github.gear4jtest.core.context.ItemExecution;
import io.github.gear4jtest.core.context.LineElementExecution;
import io.github.gear4jtest.core.model.BranchesModel;

public class BranchesLineElement extends LineElement {

	BranchesLineElement(BranchesModel branches) {
		super();
	}

	@Override
	public LineElementExecution createLineElementExecution(ItemExecution itemExecution) {
		return null;
	}

	@Override
	public Item execute(Item item) {
		BranchesExecution branchesExecution = item.getExecution().createBranchesExecution();
		return item;
	}
	
}
