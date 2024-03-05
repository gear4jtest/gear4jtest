package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration;
import io.github.gear4jtest.core.model.refactor.Container1Definition;
import io.github.gear4jtest.core.model.refactor.Container2Definition;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
import io.github.gear4jtest.core.model.refactor.FormerContainerDefinition;
import io.github.gear4jtest.core.model.refactor.IteratorDefinition;
import io.github.gear4jtest.core.model.refactor.LineDefinition;
import io.github.gear4jtest.core.model.refactor.OperationConfigurationDefinition;
import io.github.gear4jtest.core.model.refactor.OperationDefinition;
import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition;
import io.github.gear4jtest.core.model.refactor.SignalDefiinition;

public class AssemblyLineBuilder<BEGIN, IN> {

	private final AssemblyLineDefinition<BEGIN, IN> assemblyLine;
	private final ResourceFactory resourceFactory;

	AssemblyLineBuilder(AssemblyLineDefinition<BEGIN, IN> assemblyLine, ResourceFactory resourceFactory) {
		this.assemblyLine = assemblyLine;
		this.resourceFactory = resourceFactory;
	}

	public AssemblyLine<BEGIN, IN> buildAssemblyLine() {
		LineOperator startingElement = (LineOperator) buildLineElement(assemblyLine.getLineDefinition());
		return new AssemblyLine<>(startingElement);
	}

	public AssemblyLineOperator buildLineElement(LineDefinition<?, ?> lineDefinition) {
		AssemblyLineOperator root = null;
		AssemblyLineOperator parent = null;
		for (OperationDefinition<?, ?> operator : lineDefinition.getOperators()) {
			AssemblyLineOperator elem = buildLineElement(operator, parent);
			parent = elem;
			if (root == null) {
				root = parent;
			}
		}
		return LineElementFactory.buildLineElement(lineDefinition, root);
	}

//	private LineElement buildLineElement(BranchesModel<?, ?> branches, LineElement parentElement) {
//		LineElement element = LineElementFactory.buildLineElement(branches);
//		branches.getBranches().forEach(branch -> buildLineElement(branch, element));
//		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
//		return element;
//	}
//
//	private LineElement buildLineElement(BranchModel<?, ?> branch, LineElement parentElement) {
//		LineElement element = LineElementFactory.buildLineElement(branch);
//		parentElement.addNextLineElement(element);
//		LineElement parent = element;
//		for (BaseLineModel<?, ?> child : branch.getChildren()) {
//			LineElement elem = buildLineElement(child, parent);
//			parent = elem;
//		}
//		return element;
//	}

//	private LineElement buildLineElement(OperationModel<?, ?> stepa, LineElement parentElement) {
//		StepConfiguration configuration = new StepConfiguration(
//				assemblyLine.getEventHandling().getGlobalEventConfiguration(),
//				assemblyLine.getChainDefaultConfiguration().getStepLineElementDefaultConfiguration());
//		LineElement element = LineElementFactory.buildLineElement(stepa, configuration,
//				assemblyLine.getResourceFactory());
//		parentElement.addNextLineElement(element);
//		return element;
//	}

	private AssemblyLineOperator buildLineElement(ProcessingOperationDefinition<?, ?> stepa, AssemblyLineOperator parentElement) {
		StepConfiguration configuration = new StepConfiguration(
				Optional.ofNullable(assemblyLine.getConfiguration()).map(Configuration::getEventHandlingDefinition).orElse(null),
				Optional.ofNullable(assemblyLine.getConfiguration()).map(Configuration::getOperationDefaultConfiguration).orElse(null));
		AssemblyLineOperator element = LineElementFactory.buildLineElement(stepa, configuration, resourceFactory);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}
	
	private AssemblyLineOperator buildLineElement(SignalDefiinition<?> signal, AssemblyLineOperator parentElement) {
		AssemblyLineOperator element = LineElementFactory.buildLineElement(signal);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}

	private AssemblyLineOperator buildLineElement(IteratorDefinition<?> iterator, AssemblyLineOperator parentElement) {
		LineOperator lineElement = (LineOperator) buildLineElement(iterator.getElement());
		AssemblyLineOperator element = LineElementFactory.buildLineElement(iterator, lineElement);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}

	private AssemblyLineOperator buildLineElement(ContainerDefinition<?, ?> container, AssemblyLineOperator parentElement) {
		List<LineOperator> rootElements = new ArrayList<>(container.getSubLines().size());
		for (LineDefinition<?, ?> line : container.getSubLines()) {
			LineOperator elem = (LineOperator) buildLineElement(line);
			rootElements.add(elem);
		}
		AssemblyLineOperator element = LineElementFactory.buildLineElement(container, rootElements);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}

//	private LineElement buildLineElement(IteratorModel<?> stepa, LineElement parentElement) {
//		LineElement childElement = buildLineElement(stepa.getElement(), null);
//		LineElement element = LineElementFactory.buildLineElement(stepa, childElement);
//		parentElement.addNextLineElement(element);
//		return element;
//	}
//
//	private LineElement buildLineElement(ContainerModel<?, ?> stepa, LineElement parentElement) {
//		LineElement element = LineElementFactory.buildLineElement(stepa);
//		parentElement.addNextLineElement(element);
//		return element;
//	}
//
//	private LineElement buildLineElement(BaseLineModel<?, ?> object, LineElement parentElement) {
//		if (object instanceof BranchesModel) {
//			return buildLineElement((BranchesModel<?, ?>) object, parentElement);
//		} else if (object instanceof BranchModel) {
//			return buildLineElement((BranchModel<?, ?>) object, parentElement);
//		} else if (object instanceof OperationModel) {
//			return buildLineElement((OperationModel<?, ?>) object, parentElement);
//		} else if (object instanceof IteratorModel) {
//			return buildLineElement((IteratorModel<?>) object, parentElement);
//		} else if (object instanceof ContainerModel) {
//			return buildLineElement((ContainerModel<?, ?>) object, parentElement);
//		} else {
//			throw new IllegalArgumentException();
//		}
//	}

	private AssemblyLineOperator buildLineElement(OperationDefinition<?, ?> object, AssemblyLineOperator parentElement) {
		if (object instanceof ProcessingOperationDefinition) {
			return buildLineElement((ProcessingOperationDefinition<?, ?>) object, parentElement);
		} else if (object instanceof SignalDefiinition) {
			return buildLineElement((SignalDefiinition<?>) object, parentElement);
		} else if (object instanceof FormerContainerDefinition) {
			return buildLineElement((FormerContainerDefinition<?, ?>) object, parentElement);
		} else if (object instanceof IteratorDefinition<?>) {
			return buildLineElement((IteratorDefinition<?>) object, parentElement);
		} else if (object instanceof Container1Definition<?, ?, ?>) {
			return buildLineElement((Container1Definition<?, ?, ?>) object, parentElement);
		} else if (object instanceof Container2Definition<?, ?, ?, ?>) {
			return buildLineElement((Container2Definition<?, ?, ?, ?>) object, parentElement);
		} else if (object instanceof ContainerDefinition<?, ?>) {
			return buildLineElement((ContainerDefinition<?, ?>) object, parentElement);
		} else {
			throw new IllegalArgumentException();
		}
	}

	static class StepConfiguration {

		private EventHandlingDefinition eventConfiguration;

		private OperationConfigurationDefinition stepDefaultConfiguration;

		public StepConfiguration(EventHandlingDefinition eventConfiguration,
				OperationConfigurationDefinition stepDefaultConfiguration) {
			this.eventConfiguration = eventConfiguration;
			this.stepDefaultConfiguration = stepDefaultConfiguration;
		}

		public EventHandlingDefinition getEventConfiguration() {
			return eventConfiguration;
		}

		public OperationConfigurationDefinition getStepDefaultConfiguration() {
			return stepDefaultConfiguration;
		}

	}

}
