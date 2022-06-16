package io.github.gear4j.steps;

import io.github.gear4jtest.core.model.Step;

public class Step5 implements Step<Void, String> {

	@Override
	public String execute(Void object) {
		return "b";
	}

}
