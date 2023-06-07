package io.github.gear4jtest.core.event;

import java.util.UUID;

public class Event {

	private final UUID id;
	private final UUID item;
	private final String name;

	public Event(String name, UUID item) {
		this.id = UUID.randomUUID();
		this.item = item;
		this.name = name;
	}

	public UUID getId() {
		return id;
	}

	public UUID getItem() {
		return item;
	}

	public String getName() {
		return name;
	}

}
