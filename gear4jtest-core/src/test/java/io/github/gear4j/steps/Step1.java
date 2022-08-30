package io.github.gear4j.steps;

import io.github.gear4jtest.core.model.Step;

public class Step1 implements Step<String, Integer> {

	@Override
	public Integer execute(String object) {
		return 1;
	}

}
