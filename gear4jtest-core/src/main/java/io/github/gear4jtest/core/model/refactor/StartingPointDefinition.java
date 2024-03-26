package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.model.ElementModelBuilders;

public class StartingPointDefinition<X> {

	private Class<X> clazz;
	private ElementModelBuilders.TypeReference<X> typeReference;

	public StartingPointDefinition(Class<X> clazz) {
		this.clazz = clazz;
	}
	public StartingPointDefinition(ElementModelBuilders.TypeReference<X> clazz) {
		this.typeReference = clazz;
	}

	public Class<X> getClazz() {
		return clazz;
	}

}
