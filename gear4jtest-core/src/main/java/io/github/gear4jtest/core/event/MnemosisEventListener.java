package io.github.gear4jtest.core.event;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class MnemosisEventListener implements Runnable, EventListener {

	private final LinkedBlockingQueue<Event> EVENT_QUEUE = new LinkedBlockingQueue<>();

	private final List<EventProcessor> eventProcessors;

	private final MnemosisRules rules;

	@Override
	public void run() {
		try {
			Event event = null;
			while ((event = EVENT_QUEUE.take()) != null) {
				
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void triggerEvent(Event e) {
		EVENT_QUEUE.add(e); // Check rules before adding to queue
	}

}
