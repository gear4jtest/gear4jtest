package io.github.gear4jtest.core.service.steps;

public class Step7 extends Step6 {

	@Override
	public String execute(String object) {
		if (object.equals("a")) {
			throw new RuntimeException();
		}
		return value.getValue().toString() + "_" + chainContext.getValue().get("a");
	}

}
