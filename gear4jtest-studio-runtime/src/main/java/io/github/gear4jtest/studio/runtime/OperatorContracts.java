package io.github.gear4jtest.studio.runtime;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.studio.model.DataContract;

/**
 * Resolves inherited Operator parameters without instantiation or classpath
 * scanning.
 */
public final class OperatorContracts {
    private OperatorContracts() {
    }

    public record Resolved(Class<?> rawType, DataContract contract) {}

    public record Signature(Resolved input, Resolved output) {}

    public static Signature resolve(Class<?> implementation) {
        var signature = walk(implementation, Map.of(), new HashSet<>());
        var unknown = new Resolved(Object.class, DataContract.unknown());
        return signature == null ? new Signature(unknown, unknown) : signature;
    }

    private static Signature walk(Type type, Map<TypeVariable<?>, Resolved> bindings, Set<Type> visited) {
        if (!visited.add(type))
            return null;
        Class<?> raw;
        var current = new HashMap<>(bindings);
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> c) {
            raw = c;
            for (int i = 0; i < raw.getTypeParameters().length; i++)
                current.put(raw.getTypeParameters()[i], describe(parameterized.getActualTypeArguments()[i], bindings));
        } else if (type instanceof Class<?> c)
            raw = c;
        else
            return null;
        if (raw == Operator.class) {
            var parameters = raw.getTypeParameters();
            return new Signature(describe(parameters[0], current), describe(parameters[1], current));
        }
        for (var parent : raw.getGenericInterfaces()) {
            var found = walk(parent, current, new HashSet<>(visited));
            if (found != null)
                return found;
        }
        return raw.getGenericSuperclass() == null ? null : walk(raw.getGenericSuperclass(), current, visited);
    }

    private static Resolved describe(Type type, Map<TypeVariable<?>, Resolved> bindings) {
        if (type instanceof TypeVariable<?> variable)
            return bindings.getOrDefault(variable, new Resolved(Object.class, DataContract.unknown()));
        if (type instanceof Class<?> c) {
            boolean complete = c.getTypeParameters().length == 0;
            return new Resolved(c, new DataContract(c.getTypeName(), null, DataContract.Origin.INFERRED, complete,
                    complete ? hierarchy(c) : Set.of(), null));
        }
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> raw) {
            var names = new java.util.ArrayList<String>();
            boolean complete = true;
            for (var argument : p.getActualTypeArguments()) {
                var resolved = describe(argument, bindings).contract();
                complete &= resolved.complete();
                names.add(resolved.javaType() == null ? "?" : resolved.javaType());
            }
            return new Resolved(raw, new DataContract(raw.getName() + "<" + String.join(", ", names) + ">", null,
                    DataContract.Origin.INFERRED, complete, Set.of("java.lang.Object"), null));
        }
        if (type instanceof GenericArrayType array) {
            var component = describe(array.getGenericComponentType(), bindings);
            var raw = java.lang.reflect.Array.newInstance(component.rawType(), 0).getClass();
            return new Resolved(raw, new DataContract(component.contract().javaType() + "[]", null,
                    DataContract.Origin.INFERRED, component.contract().complete(), hierarchy(raw), null));
        }
        // Bounds and wildcards do not prove a concrete runtime payload contract.
        return new Resolved(Object.class, DataContract.unknown());
    }

    private static Set<String> hierarchy(Class<?> type) {
        var result = new HashSet<String>();
        collect(type, result);
        if (!type.isPrimitive())
            result.add("java.lang.Object");
        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            for (var name : hierarchy(type.getComponentType()))
                result.add(name + "[]");
        }
        return result;
    }

    private static void collect(Class<?> type, Set<String> result) {
        if (!result.add(type.getTypeName()))
            return;
        for (var parent : type.getInterfaces())
            collect(parent, result);
        if (type.getSuperclass() != null)
            collect(type.getSuperclass(), result);
    }
}
