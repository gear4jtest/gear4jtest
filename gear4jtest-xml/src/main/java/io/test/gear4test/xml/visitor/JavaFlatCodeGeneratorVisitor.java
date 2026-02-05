package io.test.gear4test.xml.visitor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.Modifier;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ContainerBaseStation;
import io.github.gear4jtest.core.model.IteratorStation;
import io.github.gear4jtest.core.model.Operator;
import io.github.gear4jtest.core.model.WorkStation;
import io.github.gear4jtest.core.model.SignalStation;
import io.github.gear4jtest.core.model.UnaryIfElseContainerStation;
import io.test.gear4jtest.xml.generated.ActionType;
import io.test.gear4jtest.xml.generated.BaseOperationType;
import io.test.gear4jtest.xml.generated.ConditionType;
import io.test.gear4jtest.xml.generated.ConditionalOperationType;
import io.test.gear4jtest.xml.generated.ConfigurationType;
import io.test.gear4jtest.xml.generated.ContainerType;
import io.test.gear4jtest.xml.generated.ContextParameterType;
import io.test.gear4jtest.xml.generated.DependenciesType;
import io.test.gear4jtest.xml.generated.IfElseContainerType;
import io.test.gear4jtest.xml.generated.IteratorType;
import io.test.gear4jtest.xml.generated.OperationType;
import io.test.gear4jtest.xml.generated.ProcessingOperationType;
import io.test.gear4jtest.xml.generated.SafeErrorType;
import io.test.gear4jtest.xml.generated.SignalType;
import io.test.gear4jtest.xml.generated.SignalTypeEnum;
import io.test.gear4jtest.xml.generated.SubLineType;
import io.test.gear4jtest.xml.generated.SupplierParameterType;
import io.test.gear4jtest.xml.generated.UnsafeErrorType;
import io.test.gear4jtest.xml.generated.ValueParameterType;
import io.test.gear4test.xml.generator.EnhancedDependencyFieldGenerator;
import io.test.gear4test.xml.generator.GeneratedAssemblyLine;

/**
 * Implémentation simplifiée du visiteur pour la génération de code Java.
 * Compatible Java 17.
 */
public class JavaFlatCodeGeneratorVisitor implements XmlToJavaVisitor {

    @Override
    public TypeSpec visit(io.test.gear4jtest.xml.generated.AssemblyLine assemblyLine, VisitorContext visitorContext) throws ClassNotFoundException {
        Objects.requireNonNull(assemblyLine, "assemblyLine");
        Objects.requireNonNull(visitorContext, "visitorContext");

        final String className = Names.toTypeName(Names.capitalize(nonEmpty(assemblyLine.getId(), "Assembly")) + "Line");

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addJavadoc("Classe générée pour l'assembly line {@code $L}.", assemblyLine.getId())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(GeneratedAssemblyLine.class);

        final var deps = Optional.ofNullable(assemblyLine.getDependencies())
                .map(DependenciesType::getDependency)
                .orElseGet(List::of);
        EnhancedDependencyFieldGenerator depsGen = new EnhancedDependencyFieldGenerator(); // TODO: gardez votre impl réelle
        depsGen.generateFields(deps).forEach(classBuilder::addField);
        classBuilder.addMethod(depsGen.generateConstructor(deps));

        for (BaseOperationType operation : assemblyLine.getOperations().getOperations()) {
            visitOperation(operation, classBuilder, visitorContext);
        }

        if (assemblyLine.getConfiguration() != null) {
            visit(assemblyLine.getConfiguration(), classBuilder, visitorContext);
        }

        visit(assemblyLine, classBuilder, visitorContext);

        return classBuilder.build();
    }

    private void visit(io.test.gear4jtest.xml.generated.AssemblyLine assemblyLine, TypeSpec.Builder classBuilder, VisitorContext visitorContext) throws ClassNotFoundException {
        var inputClassName = Class.forName(assemblyLine.getInputType());

        String methodName = "getAssemblyLineDefinition";
        CodeBlock.Builder code = CodeBlock.builder()
                .add("return $T.<$T>createAssemblyLine($S)\n", ElementModelBuilders.class, inputClassName, assemblyLine.getId());

        for (BaseOperationType op : assemblyLine.getOperations().getOperations()) {
            String opMethodName = generateMethodName(op);
            code.add(".then($L())\n", opMethodName);
        }

        if (assemblyLine.getConfiguration() != null) {
            code.add(".configuration(createConfiguration())\n");
        }
        ParameterizedTypeName ptn = ParameterizedTypeName.get(AssemblyLine.class, inputClassName, visitorContext.getLastOut());

        MethodSpec mainMethod = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ptn)
                .addJavadoc("Crée l'AssemblyLine '$L'\n@return l'AssemblyLine configurée", assemblyLine.getId())
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(mainMethod);
    }

    private void visitOperation(BaseOperationType operation, TypeSpec.Builder classBuilder, VisitorContext visitorContext) throws ClassNotFoundException {
        if (operation instanceof ProcessingOperationType processing) {
            visit(processing, classBuilder, visitorContext);
        } else if (operation instanceof SignalType signal) {
            visit(signal, classBuilder, visitorContext);
        } else if (operation instanceof IteratorType iterator) {
            visit(iterator, classBuilder, visitorContext);
        } else if (operation instanceof ContainerType container) {
            visit(container, classBuilder, visitorContext);
        } else if (operation instanceof IfElseContainerType ifElse) {
            visit(ifElse, classBuilder, visitorContext);
        } else {
            throw new IllegalArgumentException("Type d'opération non supporté: " + operation.getClass());
        }
    }

    @Override
    public void visit(ProcessingOperationType operation, TypeSpec.Builder classBuilder, VisitorContext visitorContext) throws ClassNotFoundException {
        String methodName = "process" + capitalize(operation.getId());

        CodeBlock.Builder code = CodeBlock.builder()
                .add("return processingOperation($S, $T.class)\n", operation.getId(), ClassName.get(operation.getType().substring(0, operation.getType().lastIndexOf(".")), operation.getType().substring(operation.getType().lastIndexOf(".") + 1)));

        Class<?> clazz = Class.forName(operation.getType());
        ParameterizedType operationType = Arrays.stream(clazz.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(type -> type.getRawType() == Operator.class)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Operation class does not implements Transformer"));
        Type[] argumentsTypes = operationType.getActualTypeArguments();
        ParameterizedTypeName ptn = ParameterizedTypeName.get(WorkStation.class, argumentsTypes[0], argumentsTypes[1]);

        visitorContext.setLastOut(argumentsTypes[1]);

        addParameters(operation, code);
        addErrorHandling(operation, code, argumentsTypes[0]);
        addConditions(operation, code);
        addFallback(operation, code);

        MethodSpec method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(ptn)
                .addJavadoc("Crée l'opération de traitement '$L'\n@return l'opération configurée", operation.getId())
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(method);
    }

    @Override
    public void visit(SignalType signal, TypeSpec.Builder classBuilder, VisitorContext visitorContext) {
        String methodName = "signal" + capitalize(signal.getId());
        String builderMethod = getSignalBuilderMethod(signal.getType());

        CodeBlock.Builder code = CodeBlock.builder()
                .add("return $L($L.class)\n", builderMethod, signal.getInputType());

        Optional.ofNullable(signal.getCondition())
                .map(ConditionType::getExpression)
                .ifPresent(expr -> code.add(".condition(ctx -> $L)\n", expr));

        MethodSpec method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(SignalStation.class)
                .addJavadoc("Crée le signal '$L' de type $L\n@return le signal configuré", signal.getId(), signal.getType())
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(method);
    }

    private static BaseOperationType getBaseOperationType(OperationType operation) {
        if (operation.getProcessingOperation() != null) {
            return operation.getProcessingOperation();
        } else if (operation.getSignal() != null) {
            return operation.getSignal();
        } else if (operation.getIterator() != null) {
            return operation.getIterator();
        } else if (operation.getContainer() != null) {
            return operation.getContainer();
        } else if (operation.getIfElseContainer() != null) {
            return operation.getIfElseContainer();
        }
        throw new IllegalArgumentException("Type d'opération non supporté: " + operation);
    }

    @Override
    public void visit(IteratorType iterator, TypeSpec.Builder classBuilder, VisitorContext visitorContext) {
        String methodName = "iterate" + capitalize(iterator.getId());

        var lastOut = visitorContext.getLastOut();

        CodeBlock.Builder code = CodeBlock.builder()
                .add("return $T.<$T>iterate($S)\n", ElementModelBuilders.class, lastOut, iterator.getId());

        Optional.ofNullable(iterator.getIterableFunction())
                .ifPresent(func -> code.add(".iterableFunction($L)\n", func.getExpression()));

        Optional.ofNullable(iterator.getOperation())
                .ifPresent(op -> {
                    var baseOperation = getBaseOperationType(op);
                    String opMethodName = generateMethodName(baseOperation);
                    code.add(".operation($L())\n", opMethodName);
                    try {
                        visitOperation(baseOperation, classBuilder, visitorContext);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });

        addAccumulatorCollector(iterator, code);

        if (iterator.getCollector() != null && iterator.getCollector().getExpression().equals("Collectors.toList()")) {
            visitorContext.setLastOut(new ParameterizedTypeImpl(List.class, null, new Type[]{visitorContext.getLastOut()}));
        }
        ParameterizedTypeName ptn = ParameterizedTypeName.get(IteratorStation.class, lastOut, visitorContext.getLastOut());

        MethodSpec createMethod = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(ptn)
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(createMethod);
    }

    @Override
    public void visit(ContainerType container, TypeSpec.Builder classBuilder, VisitorContext visitorContext) throws ClassNotFoundException {
        String methodName = "container" + capitalize(container.getId());

        CodeBlock.Builder code = CodeBlock.builder();

        if (container.isParallel()) {
            Optional.ofNullable(container.getThreadPoolSize())
                    .ifPresentOrElse(
                            poolSize -> code.add("return container($L.class, $T.newFixedThreadPool($L))\n",
                                    container.getInputType(), ClassName.get("java.util.concurrent", "Executors"), poolSize),
                            () -> code.add("return container($L.class, $T.newCachedThreadPool())\n",
                                    container.getInputType(), ClassName.get("java.util.concurrent", "Executors"))
                    );
        } else {
            code.add("return container($L.class)\n", container.getInputType());
        }

        if (container.getSubLines() != null && !container.getSubLines().getSubLine().isEmpty()) {
            for (SubLineType sub : container.getSubLines().getSubLine()) {
                BaseOperationType subOp = sub.getOperation().getValue();
                String subMethodName = generateMethodName(subOp);

                Optional.ofNullable(sub.getCondition())
                        .ifPresentOrElse(
                                condition -> code.add(".withSubLine($L(), (input, ctx) -> $L)\n",
                                        subMethodName, condition.getExpression()),
                                () -> code.add(".withSubLine($L())\n", subMethodName)
                        );

                visitOperation(subOp, classBuilder, visitorContext);
            }
        }

        Optional.ofNullable(container.getReturnsFunction())
                .ifPresent(func -> code.add(".returns($L)\n", func.getExpression()));

        var inputType = visitorContext.getLastOut();
        ParameterizedTypeName ptn = ParameterizedTypeName.get(ContainerBaseStation.class, inputType, visitorContext.getLastOut());

        MethodSpec method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(ptn)
                .addJavadoc("Crée le conteneur '$L'\n@return le conteneur configuré", container.getId())
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(method);
    }

    @Override
    public void visit(IfElseContainerType ifElse, TypeSpec.Builder classBuilder, VisitorContext visitorContext) throws ClassNotFoundException {
        String methodName = "ifElseContainer" + capitalize(ifElse.getId());

        CodeBlock.Builder code = CodeBlock.builder()
                .add("return ifElseContainer($L.class)\n", ifElse.getInputType());

        if (ifElse.getConditionalOperations() != null && !ifElse.getConditionalOperations().getConditionalOperation().isEmpty()) {
            for (ConditionalOperationType sub : ifElse.getConditionalOperations().getConditionalOperation()) {
                String subMethodName = generateMethodName(sub.getOperation());

                Optional.ofNullable(sub.getCondition())
                        .ifPresentOrElse(
                                condition -> code.add(".withSubLine($L(), (input, ctx) -> $L)\n",
                                        subMethodName, condition.getExpression()),
                                () -> code.add(".withSubLine($L())\n", subMethodName)
                        );

                visit(sub.getOperation(), classBuilder, visitorContext);
            }
        }

        Optional.ofNullable(ifElse.getElseOperation())
                .ifPresent(elseOp -> {
                        String opMethodName = generateMethodName(elseOp);
                        code.add(".operation($L())\n", opMethodName);
                        try {
                            visit(elseOp, classBuilder, visitorContext);
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });

        var inputType = visitorContext.getLastOut();
        ParameterizedTypeName ptn = ParameterizedTypeName.get(UnaryIfElseContainerStation.class, inputType, visitorContext.getLastOut());

        MethodSpec method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(ptn)
                .addJavadoc("Crée le conteneur if-else '$L'\n@return le conteneur configuré", ifElse.getId())
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(method);
    }

    @Override
    public void visit(ConfigurationType config, TypeSpec.Builder classBuilder, VisitorContext visitorContext) {
        CodeBlock.Builder code = CodeBlock.builder()
                .add("return configuration()\n");

        Optional.ofNullable(config.getOperationDefaultConfiguration())
                .ifPresent($ -> code.add(".stepDefaultConfiguration(operationConfiguration().build())\n"));

        Optional.ofNullable(config.getEventHandling())
                .ifPresent($ -> code.add(".eventHandlingDefinition(eventHandling().build())\n"));

        Optional.ofNullable(config.getPersistence())
                .ifPresent(persistence -> code.add(".persistence(persistenceConfiguration().persistenceType(PersistenceType.$L).build())\n",
                        persistence.getPersistenceType()));

        MethodSpec method = MethodSpec.methodBuilder("createConfiguration")
                .addModifiers(Modifier.PRIVATE)
                .returns(ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration"))
                .addJavadoc("Crée la configuration de l'AssemblyLine\n@return la configuration")
                .addCode(code.add(".build();\n").build())
                .build();

        classBuilder.addMethod(method);
    }

    // ========== Méthodes utilitaires ==========

    private String generateMethodName(BaseOperationType operation) {
        String id = operation.getId();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Opération sans ID: " + operation.getClass().getSimpleName());
        }

        if (operation instanceof ProcessingOperationType) {
            return "process" + capitalize(id);
        } else if (operation instanceof SignalType) {
            return "signal" + capitalize(id);
        } else if (operation instanceof IteratorType) {
            return "iterate" + capitalize(id);
        } else if (operation instanceof ContainerType) {
            return "container" + capitalize(id);
        } else if (operation instanceof IfElseContainerType) {
            return "ifElseContainer" + capitalize(id);
        }

        return "create" + capitalize(id);
    }

    private String getSignalBuilderMethod(SignalTypeEnum signalType) {
        return switch (signalType) {
            case FATAL -> "fatalSignal";
            case STOP -> "stopSignal";
            case IGNORE -> "ignoreSignal";
            default -> throw new IllegalArgumentException("Type de signal non supporté: " + signalType);
        };
    }

    // ========== Méthodes utilitaires existantes ==========

    private void addParameters(ProcessingOperationType op, CodeBlock.Builder code) {
        Optional.ofNullable(op.getParameters()).ifPresent(params -> {
            for (Object param : params.getValueParameterOrSupplierParameterOrContextParameter()) {
                if (param instanceof ValueParameterType valueParam) {
                    if ("java.lang.String".equals(valueParam.getValueType())) {
                        code.add(".parameter($L, $S)\n", valueParam.getRetriever(), valueParam.getValue());
                    } else {
                        code.add(".parameter($L, $L)\n", valueParam.getRetriever(), valueParam.getValue());
                    }
                } else if (param instanceof SupplierParameterType supplierParam) {
                    code.add(".parameter($L, $L)\n", supplierParam.getRetriever(), supplierParam.getSupplier());
                } else if (param instanceof ContextParameterType contextParam) {
                    code.add(".parameter($L, $L)\n", contextParam.getRetriever(), contextParam.getFunction());
                } else {
                    throw new IllegalArgumentException("Type de paramètre non supporté: " + param.getClass());
                }
            }
        });
    }

    private void addErrorHandling(ProcessingOperationType op, CodeBlock.Builder code, Type argumentsType) {
        Optional.ofNullable(op.getOnErrors()).ifPresent(errors -> {
            for (Object error : errors.getSafeErrorOrUnsafeError()) {
                addSingleErrorHandling(error, code, argumentsType);
            }
        });
    }

    private void addSingleErrorHandling(Object error, CodeBlock.Builder code, Type argumentsType) {
        SignalTypeEnum signalType = null;
        String throwableType = null;
        ConditionType condition = null;
        ActionType action = null;

        if (error instanceof SafeErrorType safeError) {
            signalType = safeError.getSignalType();
            throwableType = safeError.getThrowableType();
            condition = safeError.getCondition();
            action = safeError.getAction();
        } else if (error instanceof UnsafeErrorType unsafeError) {
            signalType = unsafeError.getSignalType();
            throwableType = unsafeError.getThrowableType();
            condition = unsafeError.getCondition();
            action = unsafeError.getAction();
        }

        if (signalType != null && throwableType != null) {
            String signal = switch (signalType) {
                case FATAL -> "fatal";
                case STOP -> "stop";
                case IGNORE -> "ignore";
                default -> throw new IllegalArgumentException("Type de signal non supporté: " + signalType);
            };

            code.add(".onError($T.<$T>$L($L.class)\n", ElementModelBuilders.class, argumentsType, signal, throwableType);

            if (condition != null) {
                code.add(".condition((input, ctx) -> $L)\n", condition.getExpression());
            }

            if (action != null) {
                code.add(".action(() -> $L)\n", action.getExpression());
            }

            code.add(".build())\n");
        }
    }

    private void addConditions(ProcessingOperationType op, CodeBlock.Builder code) {
        Optional.ofNullable(op.getConditions()).ifPresent(conditions -> {
            for (ConditionType condition : conditions.getCondition()) {
                code.add(".conditional((input, ctx) -> $L)\n", condition.getExpression());
            }
        });
    }

    private void addFallback(ProcessingOperationType op, CodeBlock.Builder code) {
        Optional.ofNullable(op.getFallbackTransformer()).ifPresent(fallback -> {
            String inputType = Optional.ofNullable(op.getInputType())
                    .map(String::toLowerCase)
                    .orElse("input");
            code.add(".transformer(($L, ctx, exec) -> $L)\n", inputType, fallback.getExpression());
        });
    }

    private void addAccumulatorCollector(IteratorType iterator, CodeBlock.Builder code) {
        Optional.ofNullable(iterator.getAccumulator())
                .ifPresentOrElse(
                        accumulator -> {
                            String accType = switch (accumulator.getType()) {
                                case LIST -> "toList()";
                                case SET -> "toSet()";
                                default -> throw new IllegalArgumentException("Type d'accumulateur non supporté: " + accumulator.getType());
                            };
                            code.add(".accumulator($L)\n", accType);
                        },
                        () -> Optional.ofNullable(iterator.getCollector())
                                .ifPresent(collector -> code.add(".collector($L)\n", collector.getExpression()))
                );
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * ParameterizedType implementation class.
     */
    private static final class ParameterizedTypeImpl implements ParameterizedType {
        private final Class<?> raw;
        private final Type useOwner;
        private final Type[] typeArguments;

        /**
         * Constructor
         *
         * @param rawClass      type
         * @param useOwner      owner type to use, if any
         * @param typeArguments formal type arguments
         */
        private ParameterizedTypeImpl(final Class<?> rawClass, final Type useOwner, final Type[] typeArguments) {
            this.raw = rawClass;
            this.useOwner = useOwner;
            this.typeArguments = Arrays.copyOf(typeArguments, typeArguments.length, Type[].class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Type getOwnerType() {
            return useOwner;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public String toString() {
            var stringBuilder = new StringBuilder(raw.getName());
            if (typeArguments.length > 0) {
                stringBuilder.append("<");
                for (int i = 0; i < typeArguments.length; i++) {
                    stringBuilder.append(typeArguments[i].toString());
                    if (i+1 < typeArguments.length) {
                        stringBuilder.append(", ");
                    }
                }
                stringBuilder.append(">");
            }
            return stringBuilder.toString();
        }
    }

    private static String nonEmpty(final String v, final String def) { return (v == null || v.isBlank()) ? def : v; }

    private static final class Names {
        static String toMethodSuffix(String id) {
            return capitalize(toJavaIdentifier(nonEmpty(id, "X")));
        }

        static String toTypeName(String id) { return toMethodSuffix(id); }

        static String toJavaIdentifier(String s) {
            if (s == null || s.isBlank()) return "X";
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                b.append(Character.isJavaIdentifierPart(c) ? c : '_');
            }
            if (!Character.isJavaIdentifierStart(b.charAt(0))) {
                b.insert(0, '_');
            }
            return b.toString();
        }

        static String capitalize(String s) {
            if (s == null || s.isBlank()) return "";
            String t = s.trim();
            return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1);
        }
    }
}
