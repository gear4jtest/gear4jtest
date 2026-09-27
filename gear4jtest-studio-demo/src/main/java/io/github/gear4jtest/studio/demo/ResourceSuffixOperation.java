package io.github.gear4jtest.studio.demo;

import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationParameter;

public final class ResourceSuffixOperation implements Operator<String, String> {
    private final Map<String, String> resources;
    private final StationParameter<String> resource = StationParameter.<String>newBuilder().build();

    public ResourceSuffixOperation(Map<String, String> resources) {
        this.resources = Map.copyOf(resources);
    }

    public StationParameter<String> getResource() {
        return resource;
    }

    @Override
    public String transform(String input, StationExecutionContext context) {
        String value = resources.get(resource.getValue());
        if (value == null)
            throw new IllegalStateException("Resource unavailable");
        return input + value;
    }
}
