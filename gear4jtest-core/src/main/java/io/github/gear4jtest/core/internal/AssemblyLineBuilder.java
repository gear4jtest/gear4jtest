package io.github.gear4jtest.core.internal;

import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.model.BranchModel;
import io.github.gear4jtest.core.model.BranchesModel;
import io.github.gear4jtest.core.model.ChainModel;
import io.github.gear4jtest.core.model.ChainModel.StepLineElementDefaultConfiguration;
import io.github.gear4jtest.core.model.EventHandling.EventConfiguration;
import io.github.gear4jtest.core.model.OperationModel;

public class AssemblyLineBuilder<BEGIN, IN> {

	private ChainModel<BEGIN, IN> chain;
	private Map<String, Object> context;

	AssemblyLineBuilder(ChainModel<BEGIN, IN> chain, Map<String, Object> context) {
		this.chain = chain;
		this.context = context;
	}

	public AssemblyLine<BEGIN, IN> buildAssemblyLine() {
		AssemblyLine<BEGIN, IN> line = new AssemblyLine<>(context);
		LineElement startingElement = buildLineElement(chain.getBranches(), null, line);
		return new AssemblyLine<>(startingElement, context);
	}

	private LineElement buildLineElement(BranchesModel<?, ?> branches, LineElement parentElement,
			AssemblyLine<BEGIN, IN> line) {
		LineElement element = LineElementFactory.buildLineElement(branches);
		branches.getBranches().forEach(branch -> buildLineElement(branch, element, line));
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}

	private LineElement buildLineElement(BranchModel<?, ?> branch, LineElement parentElement,
			AssemblyLine<BEGIN, IN> line) {
		LineElement element = LineElementFactory.buildLineElement(branch);
		parentElement.addNextLineElement(element);
		LineElement parent = element;
		for (Object child : branch.getChildren()) {
			LineElement elem = buildLineElement(child, parent, line);
			parent = elem;
		}
		return element;
	}

	private LineElement buildLineElement(OperationModel<?, ?> stepa, LineElement parentElement,
			AssemblyLine<BEGIN, IN> line) {
		StepConfiguration configuration = new StepConfiguration(chain.getEventHandling().getGlobalEventConfiguration(),
				chain.getChainDefaultConfiguration().getStepLineElementDefaultConfiguration());
		LineElement element = LineElementFactory.buildLineElement(stepa, configuration, chain.getResourceFactory());
		parentElement.addNextLineElement(element);
		return element;
	}

	private LineElement buildLineElement(Object object, LineElement parentElement, AssemblyLine<BEGIN, IN> line) {
		if (object instanceof BranchesModel) {
			return buildLineElement((BranchesModel<?, ?>) object, parentElement, line);
		} else if (object instanceof BranchModel) {
			return buildLineElement((BranchModel<?, ?>) object, parentElement, line);
		} else if (object instanceof OperationModel) {
			return buildLineElement((OperationModel<?, ?>) object, parentElement, line);
		} else {
			throw new IllegalArgumentException();
		}
	}

	static class StepConfiguration {

		private EventConfiguration eventConfiguration;

		private StepLineElementDefaultConfiguration stepLineElementDefaultConfiguration;

		public StepConfiguration(EventConfiguration eventConfiguration,
				StepLineElementDefaultConfiguration stepLineElementDefaultConfiguration) {
			this.eventConfiguration = eventConfiguration;
			this.stepLineElementDefaultConfiguration = stepLineElementDefaultConfiguration;
		}

		public EventConfiguration getEventConfiguration() {
			return eventConfiguration;
		}

		public StepLineElementDefaultConfiguration getStepLineElementDefaultConfiguration() {
			return stepLineElementDefaultConfiguration;
		}

	}

}
