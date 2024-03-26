package io.github.gear4jtest.core.internal;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.Operation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StepLineElementOperationInitiator {
	private static final Logger LOGGER = LogManager.getLogger("StepLineElementOperationInitiator");

	private final ResourceFactory resourceFactory;
	private final Class<Operation<?, ?>> operationClass;

	public StepLineElementOperationInitiator(StepLineElement stepLineElement) {
		this.resourceFactory = stepLineElement.getResourceFactory();
		this.operationClass = stepLineElement.getClazz();
	}

	public Operation<?, ?> initiate() {
		try {
			Operation<?, ?> operation = this.resourceFactory.getResource(operationClass);
			if (operation == null) {
				throw buildRuntimeException();
			}
			return operation;
		} catch (Exception e) {
			LOGGER.error("Error while initiating operation", e);
			throw buildRuntimeException(e);
		}
	}

	private static RuntimeException buildRuntimeException() {
		return buildRuntimeException(null);
	}

	private static RuntimeException buildRuntimeException(Exception e) {
		return new RuntimeException("Cannot retrieve exception", e);
	}

}
