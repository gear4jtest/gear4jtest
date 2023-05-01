package io.github.gear4jtest.core.event;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class CommonEventListener implements Runnable, EventListener {

	private final LinkedBlockingQueue<Event> EVENT_QUEUE = new LinkedBlockingQueue<>();
	
	private final List<EventProcessor> eventProcessors;
	
	private final Rules rules;

	@Override
	public void run() {
		try {
			Event event = null;
			while ((event = EVENT_QUEUE.take()) != null) {
				// check rules before processing event
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void triggerEvent(Event e) {
		EVENT_QUEUE.add(e);
	}
	
}
