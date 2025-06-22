package io.github.gear4jtest.core.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.model.refactor.EventBuss;

public class EventManager {

	private final List<EventBuss> eventBussList;
	private final Map<EventBuss, Thread> eventBussThreads;

	public EventManager(List<EventBuss> eventBussList) {
		this.eventBussList = eventBussList;
		this.eventBussThreads = new HashMap<>();
		initializeEventBusses();
	}

	private void initializeEventBusses() {
		if (eventBussList != null && !eventBussList.isEmpty()) {
			for (EventBuss eventBuss : eventBussList) {
				if (eventBuss != null) {
					var thread = new Thread(eventBuss);
					eventBussThreads.put(eventBuss, thread);
					thread.start();
				}
			}
		}
	}

	public void shutdown() {
		for (Map.Entry<EventBuss, Thread> entry : eventBussThreads.entrySet()) {
			if (entry.getValue() != null && entry.getValue().isAlive()) {
                try {
					synchronized(entry.getKey()) {
						entry.getKey().wait();
					}
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                entry.getValue().interrupt();
			}
		}
	}

	public <T extends Event> void publishEvent(T event) {
		if (eventBussList != null && !eventBussList.isEmpty()) {
			for (EventBuss eventBuss : eventBussList) {
				eventBuss.acceptEvent(event);
			}
		}
	}
}
