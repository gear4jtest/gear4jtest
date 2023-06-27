package io.github.gear4jtest.core.internal;

import java.util.Optional;

import io.github.gear4jtest.core.model.BranchModel;
import io.github.gear4jtest.core.model.BranchesModel;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.EventHandling.EventConfiguration;
import io.github.gear4jtest.core.model.OperationModel;

public class AssemblyLineBuilder<BEGIN, IN> {

	private final ChainModel<BEGIN, IN> chain;

	AssemblyLineBuilder(ChainModel<BEGIN, IN> chain) {
		this.chain = chain;
	}

	public AssemblyLine<BEGIN, IN> buildAssemblyLine() {
		LineElement startingElement = buildLineElement(chain.getBranches(), null);
		return new AssemblyLine<>(startingElement);
	}

	private LineElement buildLineElement(BranchesModel<?, ?> branches, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(branches);
		branches.getBranches().forEach(branch -> buildLineElement(branch, element));
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
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
		StepConfiguration configuration = new StepConfiguration(chain.getEventHandling().getGlobalEventConfiguration(),
				chain.getChainDefaultConfiguration().getStepLineElementDefaultConfiguration());
		LineElement element = LineElementFactory.buildLineElement(stepa, configuration, chain.getResourceFactory());
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

	static class StepConfiguration {

		private EventConfiguration eventConfiguration;

		private StepLineElementDefaultConfiguration stepDefaultConfiguration;

		public StepConfiguration(EventConfiguration eventConfiguration,
				StepLineElementDefaultConfiguration stepDefaultConfiguration) {
			this.eventConfiguration = eventConfiguration;
			this.stepDefaultConfiguration = stepDefaultConfiguration;
		}

		public EventConfiguration getEventConfiguration() {
			return eventConfiguration;
		}

		public StepLineElementDefaultConfiguration getStepDefaultConfiguration() {
			return stepDefaultConfiguration;
		}

	}

}
