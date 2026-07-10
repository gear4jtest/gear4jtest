package io.github.gear4jtest.xml.expression;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Controls which Java properties a GEL expression may read.
 * <p>
 * The secure default only reads string keys from maps. Java record components
 * and JavaBean accessors require an explicit allowlist. Policies match exact
 * runtime classes so approving a base type does not silently approve unknown
 * subclasses.
 */
@FunctionalInterface
public interface PropertyAccessPolicy {
    Object readProperty(Object target, String property);

    /**
     * Returns the secure policy used by default by {@link GearExpressionContext}.
     */
    static PropertyAccessPolicy secureDefaults() {
        return StandardPropertyAccessPolicy.SECURE_DEFAULTS;
    }

    /**
     * Starts an exact-type allowlist while retaining secure map access.
     */
    static Builder allowlist() {
        return new Builder();
    }

    /**
     * Temporary compatibility policy for applications migrating from unrestricted
     * record/JavaBean access. Each newly used accessor emits a warning.
     *
     * @deprecated Prefer {@link #allowlist()} or an inert snapshot.
     */
    @Deprecated(forRemoval = true)
    static PropertyAccessPolicy legacyBeanAccess() {
        return StandardPropertyAccessPolicy.LEGACY_BEAN_ACCESS;
    }

    /** Builds an exact-type GEL property allowlist. */
    final class Builder {
        private final Map<Class<?>, Set<String>> allowedProperties = new HashMap<>();

        private Builder() {
        }

        /** Allows every component of the supplied record type. */
        public Builder allowRecordType(Class<? extends Record> recordType) {
            Objects.requireNonNull(recordType, "recordType");
            Set<String> properties = allowedProperties.computeIfAbsent(recordType, ignored -> new HashSet<>());
            Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()).forEach(property -> {
                PropertyAccessors.requireReadable(recordType, property);
                properties.add(property);
            });
            return this;
        }

        /** Allows one public record component or JavaBean property on an exact type. */
        public Builder allowProperty(Class<?> type, String property) {
            Objects.requireNonNull(type, "type");
            PropertyAccessors.requireReadable(type, property);
            allowedProperties.computeIfAbsent(type, ignored -> new HashSet<>()).add(property);
            return this;
        }

        /**
         * Allows all public record components and JavaBean properties on an exact
         * trusted type.
         */
        public Builder allowBeanType(Class<?> type) {
            Objects.requireNonNull(type, "type");
            Set<String> properties = PropertyAccessors.readablePropertyNames(type);
            if (properties.isEmpty()) {
                throw new IllegalArgumentException("Type has no readable GEL properties: " + type.getName());
            }
            allowedProperties.computeIfAbsent(type, ignored -> new HashSet<>()).addAll(properties);
            return this;
        }

        public PropertyAccessPolicy build() {
            Map<Class<?>, Set<String>> immutableAllowlist = new HashMap<>();
            allowedProperties
                    .forEach((type, properties) -> immutableAllowlist.put(type, Set.copyOf(properties)));
            return new StandardPropertyAccessPolicy(Map.copyOf(immutableAllowlist), false);
        }
    }
}
