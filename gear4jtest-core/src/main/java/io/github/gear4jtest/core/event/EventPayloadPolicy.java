package io.github.gear4jtest.core.event;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.annotation.PublicApi;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.spi.security.RedactionTarget;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;

/**
 * Controls which built-in station event payloads are exposed to asynchronous
 * event consumers.
 * <p>
 * User custom events remain fully user-defined. This policy only affects
 * payloads attached by the core runtime when it publishes built-in station
 * events. The runtime uses {@link #discard()} when no policy is configured; raw
 * payload forwarding through {@link #passthrough()} is an explicit opt-in.
 * </p>
 */
@PublicApi
public interface EventPayloadPolicy {
    static EventPayloadPolicy passthrough() {
        return new EventPayloadPolicy() {
            @Override
            public Object mapStationInput(Object input, StationExecutionContext stationExecutionContext) {
                return input;
            }

            @Override
            public Object mapStationOutput(Object output, StationExecutionContext stationExecutionContext) {
                return output;
            }
        };
    }

    static EventPayloadPolicy discard() {
        return new EventPayloadPolicy() {
            @Override
            public Object mapStationInput(Object input, StationExecutionContext stationExecutionContext) {
                return null;
            }

            @Override
            public Object mapStationOutput(Object output, StationExecutionContext stationExecutionContext) {
                return null;
            }
        };
    }

    static EventPayloadPolicy keepIf(Predicate<Object> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new EventPayloadPolicy() {
            @Override
            public Object mapStationInput(Object input, StationExecutionContext stationExecutionContext) {
                return predicate.test(input) ? input : null;
            }

            @Override
            public Object mapStationOutput(Object output, StationExecutionContext stationExecutionContext) {
                return predicate.test(output) ? output : null;
            }
        };
    }

    static EventPayloadPolicy redacting(EventPayloadPolicy delegate, SensitiveDataRedactor redactor) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(redactor, "redactor");
        return new EventPayloadPolicy() {
            @Override
            public Object mapStationInput(Object input, StationExecutionContext context) {
                return redactor.redact(RedactionTarget.EVENT_INPUT, delegate.mapStationInput(input, context));
            }

            @Override
            public Object mapStationOutput(Object output, StationExecutionContext context) {
                return redactor.redact(RedactionTarget.EVENT_OUTPUT, delegate.mapStationOutput(output, context));
            }
        };
    }

    static EventPayloadPolicy keepOnlyTypes(Class<?>... allowedTypes) {
        Objects.requireNonNull(allowedTypes, "allowedTypes");
        Set<Class<?>> allowed = Arrays.stream(allowedTypes).filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        return keepIf(value -> value != null
                && allowed.stream()
                        .anyMatch(allowedType -> allowedType.isAssignableFrom(value.getClass())));
    }

    Object mapStationInput(Object input, StationExecutionContext stationExecutionContext);

    default Object mapStationOutput(Object output, StationExecutionContext stationExecutionContext) {
        return output;
    }
}
