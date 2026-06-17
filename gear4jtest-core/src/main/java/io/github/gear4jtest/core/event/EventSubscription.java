package io.github.gear4jtest.core.event;

import java.util.Objects;
import java.util.function.Predicate;

import io.github.gear4jtest.core.api.annotation.PublicApi;

@PublicApi
public final class EventSubscription<T extends Event> {
    private final Class<T> eventType;
    private final Predicate<? super T> predicate;
    private final EventReaction<? super T> reaction;

    private EventSubscription(Class<T> eventType, Predicate<? super T> predicate, EventReaction<? super T> reaction) {
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.predicate = predicate != null ? predicate : __ -> true;
        this.reaction = Objects.requireNonNull(reaction, "reaction");
    }

    public static <T extends Event> EventSubscription<T> on(Class<T> eventType, EventReaction<? super T> reaction) {
        return new EventSubscription<>(eventType, null, reaction);
    }

    public static <T extends Event> EventSubscription<T> on(Class<T> eventType,
                                                            Predicate<? super T> predicate,
                                                            EventReaction<? super T> reaction) {
        return new EventSubscription<>(eventType, predicate, reaction);
    }

    public boolean accepts(Event event) {
        return eventType.isInstance(event) && predicate.test(eventType.cast(event));
    }

    public void handle(Event event) throws Exception {
        reaction.handle(eventType.cast(event));
    }

    public Class<T> eventType() {
        return eventType;
    }
}
