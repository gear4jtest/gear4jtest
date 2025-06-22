package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;

public class Step13 implements Operation<String, List<String>> {

	@Override
	public List<String> execute(String string, ExecutionContext context) {
		return Arrays.asList(string);
	}

}
