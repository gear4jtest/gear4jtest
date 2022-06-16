package io.github.gear4j.steps;

import java.util.HashMap;
import java.util.Map;

import io.github.gear4jtest.core.model.Step;

public class Step3 implements Step<String, Map<String, String>> {

	@Override
	public Map<String, String> execute(String object) {
		return new HashMap<>();
	}

}
