package io.test.gear4jtest.xml.generator;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ConditionalOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SubLine;

final class OperationTypeResolver {
    private final ClassLoader classLoader;
    private final Map<Operation, OperationSignature> signatures = new IdentityHashMap<>();

    OperationTypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    private static JavaTypeName parseNullable(String value) {
        return value == null || value.isBlank() ? null : JavaTypeName.parse(value);
    }

    Map<Operation, OperationSignature> resolve(XmlPipelineDefinition definition) {
        JavaTypeName current = JavaTypeName.parse(definition.inputType());
        for (Operation operation : definition.operations()) {
            OperationSignature signature = resolve(operation, current);
            current = signature.outputType();
        }
        return Map.copyOf(signatures);
    }

    private OperationSignature resolve(Operation operation, JavaTypeName currentInput) {
        OperationSignature existing = signatures.get(operation);
        if (existing != null) {
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

        signatures.put(operation, signature);
        return signature;
    }

    private OperationSignature resolveProcessing(ProcessingOperation operation, JavaTypeName currentInput) {
        OperatorSignature operatorSignature = resolveOperatorSignature(operation.type());
        JavaTypeName declaredInput = parseNullable(operation.inputType());
        JavaTypeName effectiveInput = declaredInput != null ? declaredInput : operatorSignature.inputType();
        if (effectiveInput == JavaTypeName.OBJECT && currentInput != null) {
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

        OperationSignature childSignature = resolve(operation.operation(), null);
        JavaTypeName itemType = childSignature.inputType();
        if (JavaTypeName.OBJECT.equals(itemType) && effectiveInput.isIterableLike()) {
            itemType = effectiveInput.firstArgumentOrObject();
        }

        OperationSignature refinedChildSignature = resolve(operation.operation(), itemType);
        JavaTypeName childOutput = refinedChildSignature.outputType();
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
                if (resolved != null) {
                    return resolved;
                }
            }
            Type superType = clazz.getGenericSuperclass();
            return superType == null ? null : findOperatorArguments(superType, resolvedVariables);
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
        return null;
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

    private record OperatorSignature(JavaTypeName inputType, JavaTypeName outputType) {}

    private record ResolvedParameterizedType(Class<?> rawType, Type[] actualTypeArguments, Type ownerType)
            implements ParameterizedType {
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
    }
}
