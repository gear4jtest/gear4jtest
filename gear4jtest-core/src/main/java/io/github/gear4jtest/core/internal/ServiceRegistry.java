package io.github.gear4jtest.core.internal;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.gear4jtest.core.event.EventTriggerService;

public class ServiceRegistry {

	public static final ConcurrentMap<UUID, EventTriggerService> EVENT_TRIGGER_SERVICES = new ConcurrentHashMap<>();

	public static EventTriggerService getEventPublisher(UUID lineId) {
		return EVENT_TRIGGER_SERVICES.get(lineId);
	}

	static EventTriggerService pushEventTriggerService(UUID lineId, EventTriggerService eventTriggerService) {
		return EVENT_TRIGGER_SERVICES.put(lineId, eventTriggerService);
	}

}
