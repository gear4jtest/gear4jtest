package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.BranchModel;
import io.github.gear4jtest.core.model.BranchesModel;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.OperationModel;

public class LineElementFactory {

	private LineElementFactory() {}
	
	static LineElement buildLineElement(BranchesModel<?, ?> branches, AssemblyLine line) {
		return new BranchesLineElement(branches, line);
	}
	
	static LineElement buildLineElement(BranchModel<?, ?> branch, AssemblyLine line) {
		return new BranchLineElement(branch, line);
	}
	
	static LineElement buildLineElement(OperationModel<?, ?> step, StepLineElementDefaultConfiguration defaultConfiguration, ResourceFactory resourceFactory, AssemblyLine line) {
		return new StepLineElement(step, defaultConfiguration, resourceFactory, line);
	}

}
