package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.AssemblyLineBuilder.StepConfiguration;
import io.github.gear4jtest.core.model.BranchModel;
import io.github.gear4jtest.core.model.BranchesModel;
import io.github.gear4jtest.core.model.OperationModel;

public class LineElementFactory {

	private LineElementFactory() {}
	
	static LineElement buildLineElement(BranchesModel<?, ?> branches) {
		return new BranchesLineElement(branches);
	}
	
	static LineElement buildLineElement(BranchModel<?, ?> branch) {
		return new BranchLineElement(branch);
	}
	
	static LineElement buildLineElement(OperationModel<?, ?> step, StepConfiguration configuration, ResourceFactory resourceFactory) {
		return new StepLineElement(step, configuration, resourceFactory);
	}

}
