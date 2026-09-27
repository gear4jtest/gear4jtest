package io.github.gear4jtest.studio.runtime;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.studio.model.OperationDescriptor;

/**
 * One trusted host declaration drives both public metadata and executable
 * capability policy.
 */
public record RegisteredOperation(OperationDescriptor descriptor,
                                  Class<? extends Operator<?, ?>> type,
                                  Class<?> inputType,
                                  Class<?> outputType,
                                  Map<String, String> parameterGetters) {
    public RegisteredOperation {
        parameterGetters = Map.copyOf(parameterGetters);
        if (!descriptor.id().matches("[a-zA-Z][a-zA-Z0-9._-]{0,127}") || type.getCanonicalName() == null
                || type.getEnclosingClass() != null || !Modifier.isPublic(type.getModifiers())
                || !parameterGetters.keySet().equals(descriptor.parameters().keySet())
                || new HashSet<>(parameterGetters.values()).size() != parameterGetters.size())
            throw new IllegalArgumentException("Invalid registration");
        if (descriptor.input().javaType() != null && !descriptor.input().javaType().equals(inputType.getName()))
            throw new IllegalArgumentException("Input contract mismatch");
        if (descriptor.output().javaType() != null && !descriptor.output().javaType().equals(outputType.getName()))
            throw new IllegalArgumentException("Output contract mismatch");
        for (String getter : parameterGetters.values()) {
            try {
                var method = type.getMethod(getter);
                if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != StationParameter.class)
                    throw new IllegalArgumentException("Invalid parameter getter");
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Invalid parameter getter", e);
            }
        }
    }

    public String retriever(String parameter) {
        return type.getCanonicalName() + "::" + parameterGetters.get(parameter);
    }
}
