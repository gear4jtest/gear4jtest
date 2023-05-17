package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.internal.Item;
import io.github.gear4jtest.core.internal.LineElement;

public interface EventBuilder<T extends Event> {

	T buildEvent(Item item, LineElement element, Object... additionalObjects);
	
	String eventName();
	
}
