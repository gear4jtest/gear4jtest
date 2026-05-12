package io.github.gear4jtest.core.api.context;

import java.util.Objects;
import java.util.function.Supplier;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

/**
 * Run-scoped technical services that are safe to expose to user operations and
 * regular runtime extensions.
 *
 * <p>
 * This aggregates infrastructure-like collaborators that are part of the public
 * execution context: event publication, resource resolution and per-run
 * station-scoped resource caching.
 * </p>
 *
 * <p>
 * Internal orchestration capabilities, such as launching nested pipeline runs,
 * must not be added here. They are reserved to engine strategies so user
 * components cannot bypass validation, lineage tracking or BO traceability.
 * </p>
 */
public final class ExecutionServices {
    private final EventManager eventManager;
    private final ResourceFactory resourceFactory;
    private final StationScopedResourceRegistry stationScopedResources;

    public ExecutionServices(EventManager eventManager, ResourceFactory resourceFactory) {
        this(eventManager, resourceFactory, new StationScopedResourceRegistry());
    }

    public ExecutionServices(EventManager eventManager,
                             ResourceFactory resourceFactory,
                             StationScopedResourceRegistry stationScopedResources) {
        this.eventManager = eventManager;
        this.resourceFactory = Objects.requireNonNull(resourceFactory, "resourceFactory");
        this.stationScopedResources = stationScopedResources != null ? stationScopedResources
                : new StationScopedResourceRegistry();
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public StationScopedResourceRegistry getStationScopedResources() {
        return stationScopedResources;
    }

    public <T> T getOrCreateStationResource(String stationId, Class<T> type, Supplier<T> factory) {
        return stationScopedResources.getOrCreate(stationId, type, factory);
    }
}
