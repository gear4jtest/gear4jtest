package io.github.gear4jtest.core.internal;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.gear4jtest.core.event.SimpleEventTriggerService;

public class ServiceRegistry {

	public static final ConcurrentMap<UUID, SimpleEventTriggerService> EVENT_TRIGGER_SERVICES = new ConcurrentHashMap<>();

	public static SimpleEventTriggerService getEventPublisher(UUID lineId) {
		return EVENT_TRIGGER_SERVICES.get(lineId);
	}

	static SimpleEventTriggerService pushEventTriggerService(UUID lineId, SimpleEventTriggerService eventTriggerService) {
		return EVENT_TRIGGER_SERVICES.put(lineId, eventTriggerService);
	}

}
