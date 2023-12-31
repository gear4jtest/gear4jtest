package io.github.gear4jtest.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition;
import io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration;
import io.github.gear4jtest.core.model.refactor.ContainerDefinition;
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
		LineElement startingElement = buildLineElement(assemblyLine.getLineDefinition());
		return new AssemblyLine<>(startingElement);
	}

	public LineElement buildLineElement(LineDefinition<?, ?> lineDefinition) {
		LineElement root = null;
		LineElement parent = null;
		for (OperationDefinition<?, ?> operator : lineDefinition.getLineOperators()) {
			LineElement elem = buildLineElement(operator, parent);
			parent = elem;
			if (root == null) {
				root = parent;
			}
		}
		return root;
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

	private LineElement buildLineElement(ProcessingOperationDefinition<?, ?> stepa, LineElement parentElement) {
		StepConfiguration configuration = new StepConfiguration(
				Optional.ofNullable(assemblyLine.getConfiguration()).map(Configuration::getEventHandlingDefinition).orElse(null),
				Optional.ofNullable(assemblyLine.getConfiguration()).map(Configuration::getOperationDefaultConfiguration).orElse(null));
		LineElement element = LineElementFactory.buildLineElement(stepa, configuration, resourceFactory);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}
	
	private LineElement buildLineElement(SignalDefiinition<?> signal, LineElement parentElement) {
		LineElement element = LineElementFactory.buildLineElement(signal);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}

	private LineElement buildLineElement(ContainerDefinition<?, ?> container, LineElement parentElement) {
		List<LineElement> rootElements = new ArrayList<>(container.getChildren().size());
		for (LineDefinition<?, ?> line : container.getChildren()) {
			LineElement elem = buildLineElement(line);
			rootElements.add(elem);
		}
		LineElement element = LineElementFactory.buildLineElement(container, rootElements);
		Optional.ofNullable(parentElement).ifPresent(parentElem -> parentElem.addNextLineElement(element));
		return element;
	}

	private LineElement buildLineElement(IteratorDefinition<?> iterator, LineElement parentElement) {
		LineElement lineElement = buildLineElement(iterator.getElement(), null);
		LineElement element = LineElementFactory.buildLineElement(iterator, lineElement);
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

	private LineElement buildLineElement(OperationDefinition<?, ?> object, LineElement parentElement) {
		if (object instanceof ProcessingOperationDefinition) {
			return buildLineElement((ProcessingOperationDefinition<?, ?>) object, parentElement);
		} else if (object instanceof SignalDefiinition) {
			return buildLineElement((SignalDefiinition<?>) object, parentElement);
		} else if (object instanceof ContainerDefinition) {
			return buildLineElement((ContainerDefinition<?, ?>) object, parentElement);
		} else if (object instanceof IteratorDefinition<?>) {
			return buildLineElement((IteratorDefinition<?>) object, parentElement);
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
