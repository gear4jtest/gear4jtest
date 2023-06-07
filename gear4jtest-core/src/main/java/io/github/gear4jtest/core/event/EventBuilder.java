package io.github.gear4jtest.core.event;

import java.util.UUID;

//public interface EventBuilder<T extends Event> {
public interface EventBuilder<T, R extends Event> {

//	T buildEvent(Item item, LineElement element, Object... additionalObjects);
//	R buildEvent(UUID uuid, UUID parentUuid, T data);
	R buildEvent(UUID uuid, T data);
	
	String eventName();
	
}
