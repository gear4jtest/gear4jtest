package io.github.gear4jtest.core.api.context;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class StationScopedResourceRegistryTest {
    @Test
    void getOrCreate_shouldCachePerStationAndType() {
        StationScopedResourceRegistry registry = new StationScopedResourceRegistry();
        AtomicInteger created = new AtomicInteger();

        String first = registry.getOrCreate("station-1", String.class,
                                            () -> "value-" + created.incrementAndGet());
        String second = registry.getOrCreate("station-1", String.class,
                                             () -> "value-" + created.incrementAndGet());
        String anotherStation = registry.getOrCreate("station-2", String.class,
                                                     () -> "value-" + created.incrementAndGet());

        assertThat(first).isEqualTo("value-1");
        assertThat(second).isSameAs(first);
        assertThat(anotherStation).isEqualTo("value-2");
        assertThat(created).hasValue(2);
    }

    @Test
    void clear_shouldRemoveOnlyOneStationScopedResource() {
        StationScopedResourceRegistry registry = new StationScopedResourceRegistry();
        String original = registry.getOrCreate("station", String.class, () -> "one");
        Integer integer = registry.getOrCreate("station", Integer.class, () -> 1);

        registry.clear("station", String.class);

        assertThat(registry.getOrCreate("station", String.class, () -> "two")).isEqualTo("two");
        assertThat(registry.getOrCreate("station", Integer.class, () -> 2)).isSameAs(integer);
        assertThat(original).isEqualTo("one");
    }

    @Test
    void clearAll_shouldRemoveAllResourcesAndClearShouldIgnoreNulls() {
        StationScopedResourceRegistry registry = new StationScopedResourceRegistry();
        registry.getOrCreate("station", String.class, () -> "one");

        registry.clear(null, String.class);
        registry.clear("station", null);
        registry.clearAll();

        assertThat(registry.getOrCreate("station", String.class, () -> "two")).isEqualTo("two");
    }

    @Test
    void getOrCreate_shouldRejectNullMandatoryArguments() {
        StationScopedResourceRegistry registry = new StationScopedResourceRegistry();

        assertThatNullPointerException().isThrownBy(() -> registry.getOrCreate(null, String.class, () -> "x"));
        assertThatNullPointerException().isThrownBy(() -> registry.getOrCreate("station", null, () -> "x"));
        assertThatNullPointerException().isThrownBy(() -> registry.getOrCreate("station", String.class, null));
    }
}
