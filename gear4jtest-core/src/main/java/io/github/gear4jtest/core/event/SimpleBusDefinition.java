package io.github.gear4jtest.core.event;

import java.util.ArrayList;
import java.util.List;

public class SimpleBusDefinition {
    private String name;
    private Class<? extends EventBus> eventBusClass;
    private List<EventListener> eventListeners;
    private List<EventBusFilter> filters;

    public SimpleBusDefinition() {
        this.eventListeners = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public List<EventListener> getEventListeners() {
        return eventListeners;
    }

    public List<EventBusFilter> getFilters() {
        return filters;
    }

    public static class Builder {
        private final SimpleBusDefinition managedInstance;

        Builder(String name, Class<? extends EventBus> eventBusClass) {
            managedInstance = new SimpleBusDefinition();
            managedInstance.name = name;
            managedInstance.eventBusClass = eventBusClass;
        }

        public Builder name(String name) {
            managedInstance.name = name;
            return this;
        }

        public Builder eventListener(EventListener eventListener) {
            managedInstance.eventListeners.add(eventListener);
            return this;
        }

        public Builder filter(EventBusFilter filter) {
            if (managedInstance.filters == null) {
                managedInstance.filters = new ArrayList<>();
            }
            managedInstance.filters.add(filter);
            return this;
        }

        public SimpleBusDefinition build() {
            return this.managedInstance;
        }
    }
}
