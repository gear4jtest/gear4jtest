package io.github.gear4jtest.core.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventManager {

	private final List<EventBus> eventBusList;
	private final Map<EventBus, Thread> eventBussThreads;

	public EventManager(List<EventBus> eventBusList) {
		this.eventBusList = eventBusList;
		this.eventBussThreads = new HashMap<>();
		initializeEventBusses();
	}

	private void initializeEventBusses() {
		if (eventBusList != null) {
			for (EventBus eventBus : eventBusList) {
				if (eventBus != null) {
					var thread = new Thread(eventBus);
					eventBussThreads.put(eventBus, thread);
					thread.start();
				}
			}
		}
	}

	public void shutdown() {
		for (Map.Entry<EventBus, Thread> entry : eventBussThreads.entrySet()) {
			if (entry.getValue() != null && entry.getValue().isAlive()) {
                try {
					synchronized(entry.getKey()) {
						entry.getKey().stopBus();
					}
					entry.getValue().interrupt();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	public <T extends Event> void publish(T event) {
		if (eventBusList != null && !eventBusList.isEmpty()) {
			for (EventBus eventBus : eventBusList) {
				eventBus.acceptEvent(event);
			}
		}
	}
}
