package io.github.gear4jtest.core.internal;

import java.util.Optional;

import io.github.gear4jtest.core.model.BranchModel;
import io.github.gear4jtest.core.model.BranchesModel;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.OperationModel;
import io.github.gear4jtest.core.model.ChainModel.ChainDefaultConfiguration;

public class ChainerService {

	private ChainModel<?, ?> chain;

	ChainerService() {
	}

	public <BEGIN, IN> AssemblyLine<BEGIN, IN> buildChain(ChainModel<BEGIN, IN> chain) {
		this.chain = chain;
		LineElement startingElement = buildLineElement(chain.getBranches(), null);
		return new AssemblyLine<>(startingElement);
	}

	private LineElement buildLineElement(BranchesModel<?, ?> branches, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(branches);
		branches.getBranches().forEach(a -> buildLineElement(a, element));
		Optional.ofNullable(parentElement).ifPresent(a -> a.addNextLineElement(element));
		return element;
	}

	private LineElement buildLineElement(BranchModel<?, ?> branch, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(branch);
		parentElement.addNextLineElement(element);
		LineElement parent = element;
		for (Object child : branch.getChildren()) {
			LineElement elem = buildLineElement(child, parent);
			parent = elem;
		}
		return element;
	}

	private LineElement buildLineElement(OperationModel<?, ?> stepa, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(stepa,
				chain.getChainDefaultConfiguration().getStepLineElementDefaultConfiguration(),
				chain.getResourceFactory());
		parentElement.addNextLineElement(element);
		return element;
	}

	private LineElement buildLineElement(Object object, LineElement parentElement) {
		if (object instanceof BranchesModel) {
			return buildLineElement((BranchesModel<?, ?>) object, parentElement);
		} else if (object instanceof BranchModel) {
			return buildLineElement((BranchModel<?, ?>) object, parentElement);
		} else if (object instanceof OperationModel) {
			return buildLineElement((OperationModel<?, ?>) object, parentElement);
		} else {
			throw new IllegalArgumentException();
		}
	}

}
