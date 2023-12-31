package io.github.gear4jtest.core.model.refactor;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition.ParameterModel;

public class ProcessingOperationDataModel {

	private List<ParameterModel<?, ?>> parameters;

	public ProcessingOperationDataModel(List<ParameterModel<?, ?>> parameters) {
		this.parameters = parameters;
	}

	public List<ParameterModel<?, ?>> getParameters() {
		return new ArrayList<>(parameters);
	}

}
