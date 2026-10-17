package io.github.gear4jtest.studio.runtime;

import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationParameter;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.studio.model.DataContract;
import io.github.gear4jtest.studio.model.OperationDescriptor;

/**
 * Logical capability and host-owned bindings. Java classes never come from
 * author input.
 */
public record RegisteredOperation(OperationDescriptor descriptor,
                                  Map<ExecutionMode, Binding> bindings,
                                  Class<?> inputType,
                                  Class<?> outputType) {
    public record Binding(Class<? extends Operator<?, ?>> type, Map<String, String> parameterGetters) {
        public Binding {
            Objects.requireNonNull(type);
            parameterGetters = Map.copyOf(parameterGetters);
            if (type.getCanonicalName() == null || type.getEnclosingClass() != null
                    || !Modifier.isPublic(type.getModifiers()) || Modifier.isAbstract(type.getModifiers())
                    || new HashSet<>(parameterGetters.values()).size() != parameterGetters.size())
                throw new IllegalArgumentException("Invalid operation binding");
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
            if (!parameterGetters.containsKey(parameter))
                throw new IllegalArgumentException("Unknown parameter");
            return type.getCanonicalName() + "::" + parameterGetters.get(parameter);
        }
    }

    public RegisteredOperation {
        bindings = Map.copyOf(bindings);
        if (!descriptor.id().matches("[a-zA-Z][a-zA-Z0-9._-]{0,127}")
                || descriptor.testAllowed() != bindings.containsKey(ExecutionMode.TEST)
                || descriptor.runAllowed() != bindings.containsKey(ExecutionMode.RUN))
            throw new IllegalArgumentException("Capability flags must match runtime bindings");
        String resolvedInput = null;
        String resolvedOutput = null;
        for (var mode : ExecutionMode.values()) {
            var binding = bindings.get(mode);
            if (binding == null)
                continue;
            if (!binding.parameterGetters().keySet().equals(descriptor.parameters().keySet()))
                throw new IllegalArgumentException("Binding parameters contradict logical capability");
            var signature = OperatorContracts.resolve(binding.type());
            if (signature.input().contract().complete() && signature.input().rawType() != inputType
                    || signature.output().contract().complete() && signature.output().rawType() != outputType)
                throw new IllegalArgumentException("Declared Java contract contradicts Operator signature");
            if (bindings.size() > 1 && (!signature.input().contract().complete()
                    || !signature.output().contract().complete()))
                throw new IllegalArgumentException("Multiple modes require resolved Java contracts");
            if (resolvedInput != null && (!resolvedInput.equals(signature.input().contract().javaType())
                    || !resolvedOutput.equals(signature.output().contract().javaType())))
                throw new IllegalArgumentException("Mode bindings have different Java contracts");
            resolvedInput = signature.input().contract().javaType();
            resolvedOutput = signature.output().contract().javaType();
            descriptor = new OperationDescriptor(descriptor.id(), descriptor.name(), descriptor.description(),
                    descriptor.effects(), evidence(descriptor.input(), signature.input()),
                    evidence(descriptor.output(), signature.output()), descriptor.testAllowed(),
                    descriptor.runAllowed(), descriptor.parameters());
        }
        if (descriptor.input().javaType() != null
                && !rawName(descriptor.input().javaType()).equals(inputType.getTypeName())
                || descriptor.output().javaType() != null
                        && !rawName(descriptor.output().javaType()).equals(outputType.getTypeName()))
            throw new IllegalArgumentException("Logical Java contract mismatch");
    }

    /**
     * Convenience for one implementation explicitly enabled in one or both modes.
     */
    public RegisteredOperation(OperationDescriptor descriptor,
                               Class<? extends Operator<?, ?>> type,
                               Class<?> inputType,
                               Class<?> outputType,
                               Map<String, String> getters) {
        this(descriptor, sameImplementation(descriptor, new Binding(type, getters)), inputType, outputType);
    }

    private static Map<ExecutionMode, Binding> sameImplementation(OperationDescriptor descriptor, Binding binding) {
        var modes = new EnumMap<ExecutionMode, Binding>(ExecutionMode.class);
        if (descriptor.testAllowed())
            modes.put(ExecutionMode.TEST, binding);
        if (descriptor.runAllowed())
            modes.put(ExecutionMode.RUN, binding);
        return modes;
    }

    private static DataContract evidence(DataContract declared, OperatorContracts.Resolved inferred) {
        var java = inferred.contract();
        if (declared.javaType() == null || !java.complete())
            return declared;
        if (!declared.javaType().equals(java.javaType()))
            throw new IllegalArgumentException("Declared contract contradicts resolved generic signature");
        return new DataContract(declared.javaType(), declared.schemaId(), declared.origin(),
                declared.complete(), java.assignableTo(), declared.shape());
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
        return inferred(id, name, description, effects, type, testAllowed, false, parameters, getters);
    }

    public static RegisteredOperation inferred(String id,
                                               String name,
                                               String description,
                                               String effects,
                                               Class<? extends Operator<?, ?>> type,
                                               boolean testAllowed,
                                               boolean runAllowed,
                                               Map<String, OperationDescriptor.Parameter> parameters,
                                               Map<String, String> getters) {
        var signature = OperatorContracts.resolve(type);
        return new RegisteredOperation(new OperationDescriptor(id, name, description, effects,
                signature.input().contract(), signature.output().contract(), testAllowed, runAllowed, parameters),
                type, signature.input().rawType(), signature.output().rawType(), getters);
    }

    /**
     * Mode-neutral token in the Studio XML adapter, resolved only during code
     * generation.
     */
    public String retriever(String parameter) {
        if (!descriptor.parameters().containsKey(parameter))
            throw new IllegalArgumentException("Unknown parameter");
        return "parameter:" + parameter;
    }

    String parameter(String retriever) {
        var names = descriptor.parameters().keySet().stream().filter(name -> retriever(name).equals(retriever)
                || bindings.values().stream().anyMatch(binding -> binding.retriever(name).equals(retriever))).toList();
        if (names.size() != 1)
            throw new IllegalArgumentException("Unknown or ambiguous parameter reference");
        return names.get(0);
    }
}
