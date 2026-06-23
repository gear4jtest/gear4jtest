package io.github.gear4jtest.core.api.context;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Policy used to derive the context map of a nested run from its parent run.
 *
 * <p>
 * The default policy used by the engine is {@link #inheritAllShallow()}, which
 * preserves the historical behavior: the child receives a distinct map
 * instance, but values inside that map are shared references. Use a custom
 * policy when a nested run must receive no context, only selected keys, or
 * defensive copies of mutable values.
 * </p>
 */
@FunctionalInterface
public interface ContextPropagationPolicy {
    /**
     * Returns the context values that should be attached to the child run.
     *
     * <p>
     * Implementations should return a new mutable or immutable map and must not
     * mutate {@code parentContext}. Returning {@code null} is treated like an empty
     * context by the engine.
     * </p>
     *
     * @param parentContext immutable snapshot of the parent run context
     * @return context values to attach to the child run
     */
    Map<String, Object> propagate(Map<String, Object> parentContext);

    /**
     * Preserves all keys and values through a shallow map copy.
     */
    static ContextPropagationPolicy inheritAllShallow() {
        return parentContext -> parentContext == null || parentContext.isEmpty() ? Map.of()
                : new LinkedHashMap<>(parentContext);
    }

    /**
     * Propagates no user context to nested runs.
     */
    static ContextPropagationPolicy none() {
        return ignored -> Map.of();
    }

    /**
     * Propagates only the provided keys, preserving their values by reference.
     */
    static ContextPropagationPolicy includeKeys(String... keys) {
        Objects.requireNonNull(keys, "keys");
        return includeKeys(List.of(keys));
    }

    /**
     * Propagates only the provided keys, preserving their values by reference.
     */
    static ContextPropagationPolicy includeKeys(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys");
        List<String> selectedKeys = List.copyOf(keys);
        return parentContext -> {
            if (parentContext == null || parentContext.isEmpty() || selectedKeys.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> propagated = new LinkedHashMap<>();
            for (String key : selectedKeys) {
                if (parentContext.containsKey(key)) {
                    propagated.put(key, parentContext.get(key));
                }
            }
            return propagated;
        };
    }

    /**
     * Propagates every key after applying the provided value copier.
     *
     * <p>
     * Returning {@code null} from the copier omits that key from the child context.
     * This avoids introducing null values, which are not accepted by Gear4J's
     * runtime context map.
     * </p>
     */
    static ContextPropagationPolicy copyValues(ContextValueCopier copier) {
        return copyValues(key -> true, copier);
    }

    /**
     * Propagates matching keys after applying the provided value copier.
     *
     * <p>
     * Returning {@code null} from the copier omits that key from the child context.
     * This avoids introducing null values, which are not accepted by Gear4J's
     * runtime context map.
     * </p>
     */
    static ContextPropagationPolicy copyValues(Predicate<String> keyFilter, ContextValueCopier copier) {
        Objects.requireNonNull(keyFilter, "keyFilter");
        Objects.requireNonNull(copier, "copier");
        return parentContext -> {
            if (parentContext == null || parentContext.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> propagated = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : parentContext.entrySet()) {
                String key = entry.getKey();
                if (!keyFilter.test(key)) {
                    continue;
                }
                Object copied = copier.copy(key, entry.getValue());
                if (copied != null) {
                    propagated.put(key, copied);
                }
            }
            return propagated;
        };
    }

    /**
     * Copies a single context value before propagation.
     */
    @FunctionalInterface
    interface ContextValueCopier {
        Object copy(String key, Object value);
    }
}
