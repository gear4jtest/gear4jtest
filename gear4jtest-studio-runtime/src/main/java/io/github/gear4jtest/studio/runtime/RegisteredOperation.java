package io.github.gear4jtest.studio.runtime;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.studio.model.DataContract;
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
        var signature = OperatorContracts.resolve(type);
        if (signature.input().contract().complete() && signature.input().rawType() != inputType
                || signature.output().contract().complete() && signature.output().rawType() != outputType)
            throw new IllegalArgumentException("Declared Java contract contradicts Operator signature");
        if (!descriptor.id().matches("[a-zA-Z][a-zA-Z0-9._-]{0,127}") || type.getCanonicalName() == null
                || type.getEnclosingClass() != null || !Modifier.isPublic(type.getModifiers())
                || !parameterGetters.keySet().equals(descriptor.parameters().keySet())
                || new HashSet<>(parameterGetters.values()).size() != parameterGetters.size())
            throw new IllegalArgumentException("Invalid registration");
        if (descriptor.input().javaType() != null
                && !rawName(descriptor.input().javaType()).equals(inputType.getTypeName()))
            throw new IllegalArgumentException("Input contract mismatch");
        if (descriptor.output().javaType() != null
                && !rawName(descriptor.output().javaType()).equals(outputType.getTypeName()))
            throw new IllegalArgumentException("Output contract mismatch");
        descriptor = new OperationDescriptor(descriptor.id(), descriptor.name(), descriptor.description(),
                descriptor.effects(),
                evidence(descriptor.input(), signature.input()), evidence(descriptor.output(), signature.output()),
                descriptor.testAllowed(), descriptor.parameters());
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

    private static DataContract evidence(DataContract declared, OperatorContracts.Resolved inferred) {
        var java = inferred.contract();
        if (declared.javaType() == null || !java.complete())
            return declared;
        if (java.complete() && !declared.javaType().equals(java.javaType()))
            throw new IllegalArgumentException("Declared contract contradicts resolved generic signature");
        return new DataContract(declared.javaType(), declared.schemaId(), declared.origin(),
                declared.complete() && java.complete(), java.assignableTo(), declared.shape());
    }

    private static String rawName(String type) {
        int generic = type.indexOf('<');
        return generic < 0 ? type : type.substring(0, generic);
    }

    public static RegisteredOperation inferred(String id,
                                               String name,
                                               String description,
                                               String effects,
                                               Class<? extends Operator<?, ?>> type,
                                               boolean testAllowed,
                                               Map<String, OperationDescriptor.Parameter> parameters,
                                               Map<String, String> getters) {
        var signature = OperatorContracts.resolve(type);
        return new RegisteredOperation(new OperationDescriptor(id, name, description, effects,
                signature.input().contract(), signature.output().contract(), testAllowed, parameters),
                type, signature.input().rawType(), signature.output().rawType(), getters);
    }

    public String retriever(String parameter) {
        return type.getCanonicalName() + "::" + parameterGetters.get(parameter);
    }
}
