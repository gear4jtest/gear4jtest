package io.github.gear4jtest.xml.generator;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;

final class OperationTypeResolver {
    private final ClassLoader classLoader;
    private final Map<Operation, OperationSignature> signatures = new IdentityHashMap<>();
    private final Map<ResolutionKey, OperationSignature> cache = new HashMap<>();

    OperationTypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    private static JavaTypeName parseNullable(String value) {
        return value == null || value.isBlank() ? null : JavaTypeName.parse(value);
    }

    Map<Operation, OperationSignature> resolve(XmlAssemblyLineDefinition definition) {
        JavaTypeName current = JavaTypeName.parse(definition.inputType());
        for (Operation operation : definition.operations()) {
            OperationSignature signature = resolve(operation, current);
            current = signature.outputType();
        }
        return Map.copyOf(signatures);
    }

    private OperationSignature resolve(Operation operation, JavaTypeName currentInput) {
        ResolutionKey cacheKey = new ResolutionKey(operation, currentInput);
        OperationSignature existing = cache.get(cacheKey);
        if (existing != null) {
            signatures.put(operation, existing);
            return existing;
        }

        OperationSignature signature;
        if (operation instanceof ProcessingOperation processingOperation) {
            signature = resolveProcessing(processingOperation, currentInput);
        } else if (operation instanceof IteratorOperation iteratorOperation) {
            signature = resolveIterator(iteratorOperation, currentInput);
        } else if (operation instanceof ContainerOperation containerOperation) {
            signature = resolveContainer(containerOperation, currentInput);
        } else if (operation instanceof IfElseOperation ifElseOperation) {
            signature = resolveIfElse(ifElseOperation, currentInput);
        } else if (operation instanceof SignalOperation signalOperation) {
            signature = resolveSignal(signalOperation, currentInput);
        } else {
            throw new IllegalArgumentException("Unsupported operation type: " + operation);
        }

        cache.put(cacheKey, signature);
        signatures.put(operation, signature);
        return signature;
    }

    private OperationSignature resolveProcessing(ProcessingOperation operation, JavaTypeName currentInput) {
        OperatorSignature operatorSignature = resolveOperatorSignature(operation.type());
        JavaTypeName declaredInput = parseNullable(operation.inputType());
        JavaTypeName effectiveInput = declaredInput != null ? declaredInput : operatorSignature.inputType();
        if (JavaTypeName.OBJECT.equals(effectiveInput) && currentInput != null) {
            effectiveInput = currentInput;
        }

        JavaTypeName outputType = operatorSignature.outputType();
        if (operation.fallbackTransformer() != null && operation.fallbackTransformer().outputType() != null) {
            outputType = JavaTypeName.parse(operation.fallbackTransformer().outputType());
        }
        return new OperationSignature(effectiveInput, outputType);
    }

    private OperationSignature resolveIterator(IteratorOperation operation, JavaTypeName currentInput) {
        JavaTypeName explicitInput = parseNullable(operation.inputType());
        JavaTypeName effectiveInput = currentInput != null ? currentInput : explicitInput;
        if (effectiveInput == null) {
            effectiveInput = JavaTypeName.OBJECT;
        }

        JavaTypeName childInput = effectiveInput.isIterableLike() ? effectiveInput.firstArgumentOrObject() : null;
        OperationSignature childSignature = resolve(operation.operation(), childInput);
        JavaTypeName childOutput = childSignature.outputType();
        JavaTypeName outputType = resolveIteratorOutput(operation, childOutput);
        return new OperationSignature(effectiveInput, outputType);
    }

    private JavaTypeName resolveIteratorOutput(IteratorOperation operation, JavaTypeName childOutput) {
        if (operation.collector() != null) {
            String collector = operation.collector().replace(" ", "");
            if (collector.equals("java.util.stream.Collectors.toList()") || collector.equals("Collectors.toList()")
                    || collector.equals("toList()")) {
                return JavaTypeName.parameterized("java.util.List", childOutput);
            }
            if (collector.equals("java.util.stream.Collectors.toSet()") || collector.equals("Collectors.toSet()")
                    || collector.equals("toSet()")) {
                return JavaTypeName.parameterized("java.util.Set", childOutput);
            }
        }
        if (operation.accumulator() != null) {
            return switch (operation.accumulator().toUpperCase(java.util.Locale.ROOT)) {
                case "LIST" -> JavaTypeName.parameterized("java.util.List", childOutput);
                case "SET" -> JavaTypeName.parameterized("java.util.Set", childOutput);
                default -> parseNullable(operation.outputType()) != null ? JavaTypeName.parse(operation.outputType())
                        : JavaTypeName.OBJECT;
            };
        }
        JavaTypeName explicitOutput = parseNullable(operation.outputType());
        return explicitOutput != null ? explicitOutput : childOutput;
    }

    private OperationSignature resolveContainer(ContainerOperation operation, JavaTypeName currentInput) {
        JavaTypeName inputType = parseNullable(operation.inputType());
        if (inputType == null) {
            inputType = currentInput != null ? currentInput : JavaTypeName.OBJECT;
        }
        for (SubLine subLine : operation.subLines()) {
            resolve(subLine.operation(), inputType);
        }
        JavaTypeName outputType = parseNullable(operation.outputType());
        return new OperationSignature(inputType, outputType != null ? outputType : inputType);
    }

    private OperationSignature resolveIfElse(IfElseOperation operation, JavaTypeName currentInput) {
        JavaTypeName inputType = parseNullable(operation.inputType());
        if (inputType == null) {
            inputType = currentInput != null ? currentInput : JavaTypeName.OBJECT;
        }
        for (ConditionalOperation conditionalOperation : operation.conditionalOperations()) {
            resolve(conditionalOperation.operation(), inputType);
        }
        if (operation.elseOperation() != null) {
            resolve(operation.elseOperation(), inputType);
        }
        JavaTypeName outputType = parseNullable(operation.outputType());
        return new OperationSignature(inputType, outputType != null ? outputType : inputType);
    }

    private OperationSignature resolveSignal(SignalOperation operation, JavaTypeName currentInput) {
        JavaTypeName inputType = parseNullable(operation.inputType());
        if (inputType == null) {
            inputType = currentInput != null ? currentInput : JavaTypeName.OBJECT;
        }
        return new OperationSignature(inputType, inputType);
    }

    private OperatorSignature resolveOperatorSignature(String className) {
        try {
            Class<?> operatorClass = Class.forName(className, false, classLoader);
            Type[] arguments = findOperatorArguments(operatorClass, new HashMap<>());
            if (arguments == null || arguments.length != 2) {
                throw new IllegalArgumentException(
                        "Unable to resolve Operator<IN, OUT> generic parameters for " + className);
            }
            return new OperatorSignature(JavaTypeName.from(arguments[0]), JavaTypeName.from(arguments[1]));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Unable to load operator class '" + className + "' to resolve its generic signature", e);
        }
    }

    private Type[] findOperatorArguments(Type type, Map<TypeVariable<?>, Type> resolvedVariables) {
        if (type instanceof Class<?> clazz) {
            for (Type interfaceType : clazz.getGenericInterfaces()) {
                Type[] resolved = findOperatorArguments(interfaceType, resolvedVariables);
                if (resolved.length == 2) {
                    return resolved;
                }
            }
            Type superType = clazz.getGenericSuperclass();
            return superType == null ? new Type[0] : findOperatorArguments(superType, resolvedVariables);
        }

        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass) {
                TypeVariable<?>[] variables = rawClass.getTypeParameters();
                Type[] actualArguments = parameterizedType.getActualTypeArguments();
                Map<TypeVariable<?>, Type> childVariables = new HashMap<>(resolvedVariables);
                for (int i = 0; i < variables.length; i++) {
                    childVariables.put(variables[i], resolveType(actualArguments[i], resolvedVariables));
                }

                if (Operator.class.equals(rawClass)) {
                    return new Type[] { resolveType(actualArguments[0], resolvedVariables),
                            resolveType(actualArguments[1], resolvedVariables) };
                }
                return findOperatorArguments(rawClass, childVariables);
            }
        }
        return new Type[0];
    }

    private Type resolveType(Type type, Map<TypeVariable<?>, Type> variables) {
        if (type instanceof TypeVariable<?> variable) {
            return variables.getOrDefault(variable, Object.class);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type[] arguments = parameterizedType.getActualTypeArguments();
            Type[] resolvedArguments = new Type[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                resolvedArguments[i] = resolveType(arguments[i], variables);
            }
            return new ResolvedParameterizedType((Class<?>) parameterizedType.getRawType(), resolvedArguments,
                    parameterizedType.getOwnerType());
        }
        return type;
    }

    private static final class ResolutionKey {
        private final Operation operation;
        private final JavaTypeName currentInput;

        private ResolutionKey(Operation operation, JavaTypeName currentInput) {
            this.operation = operation;
            this.currentInput = currentInput;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ResolutionKey other)) {
                return false;
            }
            return operation == other.operation && Objects.equals(currentInput, other.currentInput);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(operation) + Objects.hashCode(currentInput);
        }
    }

    private record OperatorSignature(JavaTypeName inputType, JavaTypeName outputType) {}

    private record ResolvedParameterizedType(Class<?> rawType, Type[] actualTypeArguments, Type ownerType)
            implements ParameterizedType {
        private ResolvedParameterizedType {
            actualTypeArguments = actualTypeArguments == null ? new Type[0] : actualTypeArguments.clone();
        }

        @Override
        public Type[] actualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ResolvedParameterizedType that)) {
                return false;
            }
            return Objects.equals(rawType, that.rawType)
                    && Arrays.equals(actualTypeArguments, that.actualTypeArguments)
                    && Objects.equals(ownerType, that.ownerType);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(rawType, ownerType);
            result = 31 * result + Arrays.hashCode(actualTypeArguments);
            return result;
        }

        @Override
        public String toString() {
            return "ResolvedParameterizedType["
                    + "rawType=" + rawType
                    + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments)
                    + ", ownerType=" + ownerType
                    + ']';
        }
    }
}
