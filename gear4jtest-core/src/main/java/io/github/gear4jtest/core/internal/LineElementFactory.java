package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.model.BranchModel;
import io.github.gear4jtest.core.model.BranchesModel;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.OperationModel;

public class LineElementFactory {

	private LineElementFactory() {}
	
	static LineElement buildLineElement(BranchesModel<?, ?> branches) {
		return new BranchesLineElement(branches);
	}
	
	static LineElement buildLineElement(BranchModel<?, ?> branch) {
		return new BranchLineElement(branch);
	}
	
	static LineElement buildLineElement(OperationModel<?, ?> step, StepLineElementDefaultConfiguration defaultConfiguration) {
		return new StepLineElement(step, defaultConfiguration);
	}
	
}
