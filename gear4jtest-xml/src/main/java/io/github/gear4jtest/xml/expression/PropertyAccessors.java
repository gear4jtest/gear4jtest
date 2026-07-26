package io.github.gear4jtest.xml.expression;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class PropertyAccessors {
    private static final ClassValue<Map<String, MethodHandle>> ACCESSORS = new ClassValue<>() {
        @Override
        protected Map<String, MethodHandle> computeValue(Class<?> type) {
            return findAccessors(type);
        }
    };

    private PropertyAccessors() {
    }

    static Object read(Object target, String property) {
        MethodHandle accessor = requireReadable(target.getClass(), property);
        try {
            return accessor.invoke(target);
        } catch (Error error) {
            throw error;
        } catch (Throwable throwable) {
            throw new GearExpressionException(
                    "Unable to read property '" + property + "' from " + target.getClass().getName(), throwable);
        }
    }

    static MethodHandle requireReadable(Class<?> type, String property) {
        StandardPropertyAccessPolicy.validateObjectPropertyName(property);
        MethodHandle accessor = ACCESSORS.get(type).get(property);
        if (accessor == null) {
            throw new GearExpressionException("No readable GEL property '" + property + "' on " + type.getName());
        }
        return accessor;
    }

    static Set<String> readablePropertyNames(Class<?> type) {
        return ACCESSORS.get(type).keySet();
    }

    private static Map<String, MethodHandle> findAccessors(Class<?> type) {
        Map<String, MethodHandle> accessors = new HashMap<>();
        RecordComponent[] components = type.getRecordComponents();
        if (components != null) {
            for (RecordComponent component : components) {
                putAccessor(accessors, component.getName(), component.getAccessor());
            }
        }
        for (Method method : type.getMethods()) {
            String property = beanPropertyName(method);
            if (property != null && !accessors.containsKey(property)) {
                putAccessor(accessors, property, method);
            }
        }
        return Collections.unmodifiableMap(accessors);
    }

    private static void putAccessor(Map<String, MethodHandle> accessors, String property, Method method) {
        if (StandardPropertyAccessPolicy.isForbiddenObjectPropertyName(property)) {
            return;
        }
        try {
            accessors.put(property, MethodHandles.publicLookup().unreflect(method));
        } catch (IllegalAccessException ignored) {
            // Non-public declaring types are deliberately not opened reflectively.
        }
    }

    private static String beanPropertyName(Method method) {
        if (!Modifier.isPublic(method.getModifiers())
                || Modifier.isStatic(method.getModifiers())
                || method.getParameterCount() != 0
                || method.getReturnType() == Void.TYPE
                || method.getDeclaringClass() == Object.class) {
            return null;
        }
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is")
                && name.length() > 2
                && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static String decapitalize(String suffix) {
        if (suffix.length() > 1 && Character.isUpperCase(suffix.charAt(0)) && Character.isUpperCase(suffix.charAt(1))) {
            return suffix;
        }
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }
}
