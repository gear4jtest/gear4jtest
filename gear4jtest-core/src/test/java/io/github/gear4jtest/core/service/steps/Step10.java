package io.github.gear4jtest.core.service.steps;

import java.util.Arrays;
import java.util.List;

import io.github.gear4jtest.core.model.Operation;
import io.github.gear4jtest.core.model.refactor.ExecutionContext;

public class Step10 implements Operation<Integer, List<String>> {

	@Override
	public List<String> execute(Integer integer, ExecutionContext context) {
		return Arrays.asList("");
	}

}
