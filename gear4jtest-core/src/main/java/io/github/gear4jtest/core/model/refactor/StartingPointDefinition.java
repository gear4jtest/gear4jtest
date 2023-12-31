package io.github.gear4jtest.core.model.refactor;

public class StartingPointDefinition<X> {

	private Class<X> clazz;

	public StartingPointDefinition(Class<X> clazz) {
		this.clazz = clazz;
	}

	public Class<X> getClazz() {
		return clazz;
	}

}
