package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.context.StepExecution;
import io.github.gear4jtest.core.model.Operation;

import java.util.Arrays;
import java.util.List;

public class Step13 implements Operation<String, List<String>> {

	@Override
	public List<String> execute(String string, StepExecution context) {
		return Arrays.asList(string);
	}

}
