package io.github.gear4jtest.xml.expression;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StandardPropertyAccessPolicy implements PropertyAccessPolicy {
    static final PropertyAccessPolicy SECURE_DEFAULTS = new StandardPropertyAccessPolicy(Map.of(), false);
    static final PropertyAccessPolicy LEGACY_BEAN_ACCESS = new StandardPropertyAccessPolicy(Map.of(), true);

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardPropertyAccessPolicy.class);
    private static final Set<String> FORBIDDEN_OBJECT_PROPERTY_NAMES = Set.of(
                                                                              "class",
                                                                              "getClass",
                                                                              "metaClass",
                                                                              "toString",
                                                                              "hashCode",
                                                                              "equals",
                                                                              "clone",
                                                                              "finalize",
                                                                              "wait",
                                                                              "notify",
                                                                              "notifyAll");

    private final Map<Class<?>, Set<String>> allowedProperties;
    private final boolean legacyBeanAccess;
    private final ClassValue<Set<String>> warnedLegacyAccessors = new ClassValue<>() {
        @Override
        protected Set<String> computeValue(Class<?> type) {
            return ConcurrentHashMap.newKeySet();
        }
    };

    StandardPropertyAccessPolicy(Map<Class<?>, Set<String>> allowedProperties, boolean legacyBeanAccess) {
        this.allowedProperties = allowedProperties;
        this.legacyBeanAccess = legacyBeanAccess;
    }

    @Override
    public Object readProperty(Object target, String property) {
        if (target == null) {
            return null;
        }
        if (target instanceof InertValueMap map) {
            return map.get(property);
        }
        if (target instanceof Map<?, ?>) {
            throw new GearExpressionException("GEL map access requires an inert context snapshot");
        }
        rejectSensitiveRuntimeObject(target);
        validateObjectPropertyName(property);
        Class<?> type = target.getClass();
        if (!legacyBeanAccess && !allowedProperties.getOrDefault(type, Set.of()).contains(property)) {
            throw denied(type, property);
        }
        MethodHandle ignored = PropertyAccessors.requireReadable(type, property);
        if (legacyBeanAccess) {
            warnLegacyAccessor(type, property);
        }
        return PropertyAccessors.read(target, property);
    }

    static boolean isForbiddenObjectPropertyName(String property) {
        return FORBIDDEN_OBJECT_PROPERTY_NAMES.contains(property);
    }

    static void validateObjectPropertyName(String property) {
        if (property == null || property.isBlank()) {
            throw new GearExpressionException("GEL property name must not be blank");
        }
        if ("class".equals(property) || "getClass".equals(property) || "metaClass".equals(property)) {
            throw new GearExpressionException("Access to Java class metadata is forbidden in GEL: " + property);
        }
        if (isForbiddenObjectPropertyName(property)) {
            throw new GearExpressionException("No readable property '" + property + "' on safe GEL objects");
        }
    }

    private static void rejectSensitiveRuntimeObject(Object target) {
        if (target instanceof Class<?>
                || target instanceof ClassLoader
                || target instanceof Module
                || target instanceof Package
                || target instanceof AccessibleObject
                || target instanceof Member
                || target instanceof MethodHandle
                || target instanceof MethodHandles.Lookup) {
            throw new GearExpressionException(
                    "Access to Java runtime metadata is forbidden in GEL: " + target.getClass().getName());
        }
    }

    private static GearExpressionException denied(Class<?> type, String property) {
        return new GearExpressionException("GEL property '" + property + "' is not allowlisted for " + type.getName()
                + "; use PropertyAccessPolicy.allowlist() or convert the input to an inert value tree");
    }

    private void warnLegacyAccessor(Class<?> type, String property) {
        if (warnedLegacyAccessors.get(type).add(property)) {
            String accessor = type.getName() + "#" + property;
            LOGGER.warn("GEL legacy property access invoked {}. Configure an explicit PropertyAccessPolicy allowlist; "
                    + "legacyBeanAccess() is temporary and unsafe for untrusted object graphs.", accessor);
        }
    }
}
