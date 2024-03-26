package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.internal.AssemblyLineBuilder.StepConfiguration;
import io.github.gear4jtest.core.model.refactor.*;

import java.util.List;

public class LineElementFactory {

	private LineElementFactory() {
	}
//
//	static LineElement buildLineElement(BranchesModel<?, ?> branches) {
//		return new BranchesLineElement(branches);
//	}
//
//	static LineElement buildLineElement(BranchModel<?, ?> branch) {
//		return new BranchLineElement(branch);
//	}
//
//	static LineElement buildLineElement(OperationModel<?, ?> step, StepConfiguration configuration,
//			ResourceFactory resourceFactory) {
//		return new StepLineElement(step, configuration, resourceFactory);
//	}

//	static LineElement buildLineElement(IteratorModel<?> iterator, LineElement element) {
//		return new IteratorLineElement(iterator, element);
//	}

//	static LineElement buildLineElement(ContainerModel<?, ?> container, LineElement element) {
//		return new IteratorLineElement(container.getFunc(), element);
//	}

	static AssemblyLineOperator buildLineElement(LineDefinition<?, ?> lineDefinition, AssemblyLineOperator firstElement) {
		return new LineOperator(firstElement, lineDefinition);
	}

	static AssemblyLineOperator buildLineElement(ProcessingOperationDefinition<?, ?> step, StepConfiguration configuration,
			ResourceFactory resourceFactory) {
		return new StepLineElement(step, configuration, resourceFactory);
	}

	static AssemblyLineOperator buildLineElement(SignalDefiinition<?> signal) {
		return new SignalLineElement(signal);
	}

	static AssemblyLineOperator buildLineElement(ContainerBaseDefinition<?, ?> container, List<LineOperator> rootElements) {
		return new ContainerLineElement(container, rootElements);
	}

	static AssemblyLineOperator buildLineElement(IteratorDefinition<?, ?> iterator, LineOperator lineElement) {
		return new IteratorLineElement(iterator, lineElement);
	}

}
