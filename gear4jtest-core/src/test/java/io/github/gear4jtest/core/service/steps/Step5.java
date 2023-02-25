package io.github.gear4jtest.core.service.steps;

import io.github.gear4jtest.core.model.Operation;

public class Step5 implements Operation<Void, String> {

	@Override
	public String execute(Void object) {
		return "b";
	}

}
