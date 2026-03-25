//package io.test.gear4test.xml.generator;
//
//import java.io.File;
//import java.io.InputStream;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.Map;
//import java.util.Set;
//import javax.lang.model.element.Modifier;
//
//import com.palantir.javapoet.ClassName;
//import com.palantir.javapoet.CodeBlock;
//import com.palantir.javapoet.JavaFile;
//import com.palantir.javapoet.MethodSpec;
//import com.palantir.javapoet.TypeSpec;
//import io.test.gear4jtest.xml.generated.ActionType;
//import io.test.gear4jtest.xml.generated.AssemblyLine;
//import io.test.gear4jtest.xml.generated.BaseOperationType;
//import io.test.gear4jtest.xml.generated.ConditionType;
//import io.test.gear4jtest.xml.generated.ConditionalOperationType;
//import io.test.gear4jtest.xml.generated.ConfigurationType;
//import io.test.gear4jtest.xml.generated.ContainerType;
//import io.test.gear4jtest.xml.generated.ContextParameterType;
//import io.test.gear4jtest.xml.generated.IfElseContainerType;
//import io.test.gear4jtest.xml.generated.IteratorType;
//import io.test.gear4jtest.xml.generated.ProcessingOperationType;
//import io.test.gear4jtest.xml.generated.SafeErrorType;
//import io.test.gear4jtest.xml.generated.SignalType;
//import io.test.gear4jtest.xml.generated.SignalTypeEnum;
//import io.test.gear4jtest.xml.generated.SubLineType;
//import io.test.gear4jtest.xml.generated.SupplierParameterType;
//import io.test.gear4jtest.xml.generated.UnsafeErrorType;
//import io.test.gear4jtest.xml.generated.ValueParameterType;
//import jakarta.xml.bind.JAXBContext;
//import jakarta.xml.bind.JAXBException;
//import jakarta.xml.bind.Unmarshaller;
//
//public class XmlToJavaGeneratorV2 {
//    private static final String BUILDERS = "io.github.gear4jtest.core.api.util.ElementModelBuilders";
//    private final String pkg, cls;
//    private int cnt = 1;
//    private final Set<String> gen = new HashSet<>();
//    private final Map<String, TypeSpec> nested = new HashMap<>();
//    private final Map<BaseOperationType, String> names = new HashMap<>();
//
//    public XmlToJavaGeneratorV2(String pkg, String cls) {
//        this.pkg = pkg;
//        this.cls = cls;
//    }
//
//    public JavaFile generateFromAssemblyLine(File xmlPath) throws Exception {
//        AssemblyLine assemblyLine = unmarshal(xmlPath.toURI().toURL().openStream(), AssemblyLine.class);
//        TypeSpec.Builder builder = TypeSpec.classBuilder(cls)
//                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
//                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
//
//        AssemblyLineVisitor visitor = new AssemblyLineVisitor(builder);
//        visitor.visit(assemblyLine);
//
//        nested.values().forEach(builder::addType);
//        return JavaFile.builder(pkg, builder.build())
//                .addStaticImport(ClassName.bestGuess(BUILDERS), "*")
//                .build();
//    }
//
//    private class AssemblyLineVisitor {
//        private final TypeSpec.Builder mainBuilder;
//
//        AssemblyLineVisitor(TypeSpec.Builder builder) {
//            this.mainBuilder = builder;
//        }
//
//        void visit(AssemblyLine assemblyLine) {
//            assignNames(assemblyLine);
//            mainBuilder.addMethod(createMainMethod(assemblyLine));
//
//            for (BaseOperationType op : assemblyLine.getOperations().getOperations()) {
//                visit(op, mainBuilder);
//            }
//
//            if (assemblyLine.getConfiguration() != null) {
//                mainBuilder.addMethod(createConfigMethod(assemblyLine.getConfiguration()));
//            }
//        }
//
//        void visit(BaseOperationType op, TypeSpec.Builder builder) {
//            String name = names.get(op);
//            if (gen.contains(name)) return;
//            gen.add(name);
//
//            if (op instanceof ProcessingOperationType processingOperationType) {
//                builder.addMethod(createProcessingMethod(processingOperationType));
//            } else if (op instanceof SignalType signalType) {
//                builder.addMethod(createSignalMethod(signalType));
//            } else if (op instanceof IteratorType || op instanceof ContainerType || op instanceof IfElseContainerType) {
//                createNestedType(op, builder);
//            } else {
//                throw new IllegalArgumentException("Unsupported operation: " + op.getClass());
//            }
//        }
//
//        private void createNestedType(BaseOperationType op, TypeSpec.Builder parentBuilder) {
//            String clsName = cap(op.getClass().getSimpleName()) + (cnt++);
//            TypeSpec.Builder nestedBuilder = TypeSpec.classBuilder(clsName)
//                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC);
//
//            visitChildren(op, nestedBuilder);
//            MethodSpec method = createOperationMethod(op, clsName);
//            nestedBuilder.addMethod(method);
//            nested.put(clsName, nestedBuilder.build());
//
//            parentBuilder.addMethod(MethodSpec.methodBuilder(names.get(op))
//                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
//                    .returns(getReturnType(op))
//                    .addCode("return $L.$L();\n", clsName, method.name)
//                    .build());
//        }
//
//        private void visitChildren(BaseOperationType op, TypeSpec.Builder builder) {
//            if (op instanceof IteratorType iteratorType) {
//                if (iteratorType.getOperation() != null) {
//                    visit(iteratorType.getOperation(), builder);
//                }
//            } else if (op instanceof ContainerType containerType) {
//                for (SubLineType sub : containerType.getSubLines().getSubLine()) {
//                    BaseOperationType subOp = sub.getOperation().getValue();
//                    visit(subOp, builder);
//                }
//            } else if (op instanceof IfElseContainerType ifElseContainerType) {
//                for (ConditionalOperationType condOp : ifElseContainerType.getConditionalOperations().getConditionalOperation()) {
//                    visit(condOp.getOperation(), builder);
//                }
//                if (ifElseContainerType.getElseOperation() != null) {
//                    visit(ifElseContainerType.getElseOperation(), builder);
//                }
//            }
//        }
//
//        private MethodSpec createMainMethod(AssemblyLine assemblyLine) {
//            CodeBlock.Builder code = CodeBlock.builder()
//                    .add("return $T.<$L>createAssemblyLine($S)\n",
//                            ClassName.bestGuess(BUILDERS), assemblyLine.getInputType(), assemblyLine.getId());
//
//            for (BaseOperationType op : assemblyLine.getOperations().getOperations()) {
//                code.add("    .then($L())\n", names.get(op));
//            }
//
//            if (assemblyLine.getConfiguration() != null) {
//                code.add("    .configuration(createConfiguration())\n");
//            }
//
//            return MethodSpec.methodBuilder("create" + cap(assemblyLine.getId()))
//                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
//                    .returns(ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition"))
//                    .addCode(code.add("    .build()").build())
//                    .build();
//        }
//
//        private MethodSpec createOperationMethod(BaseOperationType op, String className) {
//            CodeBlock codeBlock = null;
//            if (op instanceof IteratorType iteratorType) {
//                codeBlock = createIteratorCode(iteratorType);
//            } else if (op instanceof ContainerType containerType) {
//                codeBlock = createContainerCode(containerType);
//            } else if (op instanceof IfElseContainerType ifElseContainerType) {
//                codeBlock = createIfElseCode(ifElseContainerType);
//            } else {
//                throw new IllegalArgumentException("Unsupported operation type: " + op.getClass());
//            }
//            return MethodSpec.methodBuilder("create" + className)
//                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
//                    .returns(getReturnType(op))
//                    .addCode(codeBlock)
//                    .build();
//        }
//
//        private MethodSpec createProcessingMethod(ProcessingOperationType op) {
//            CodeBlock.Builder code = CodeBlock.builder()
//                    .add("return processingOperation($S,$L.class)\n", op.getId(), op.getType());
//
//            addParameters(op, code);
//            addErrorHandling(op, code);
//            addConditions(op, code);
//            addFallback(op, code);
//
//            return MethodSpec.methodBuilder(names.get(op))
//                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
//                    .returns(getReturnType(op))
//                    .addCode(code.add("    .build()").build())
//                    .build();
//        }
//
//        private MethodSpec createSignalMethod(SignalType signal) {
//            CodeBlock.Builder code = CodeBlock.builder();
//            String methodName = switch (signal.getType()) {
//                case FATAL -> "fatalSignal";
//                case STOP -> "stopSignal";
//                case IGNORE -> "ignoreSignal";
//            };
//
//            code.add("return $L($L.class)\n", methodName, signal.getInputType());
//            if (signal.getCondition() != null) {
//                code.add("    .condition(ctx->$L)\n", signal.getCondition().getExpression());
//            }
//
//            return MethodSpec.methodBuilder(names.get(signal))
//                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
//                    .returns(getReturnType(signal))
//                    .addCode(code.add("    .build()").build())
//                    .build();
//        }
//
//        private CodeBlock createIteratorCode(IteratorType iter) {
//            CodeBlock.Builder code = CodeBlock.builder()
//                    .add("return iterate($S)\n", iter.getId());
//
//            if (iter.getIterableFunction() != null) {
//                code.add("    .iterableFunction($L)\n", iter.getIterableFunction().getExpression());
//            }
//            if (iter.getOperation() != null) {
//                code.add("    .operation($L())\n", names.get(iter.getOperation()));
//            }
//
//            addAccumulatorCollector(iter, code);
//            return code.add("    .build()").build();
//        }
//
//        private CodeBlock createContainerCode(ContainerType cont) {
//            CodeBlock.Builder code = CodeBlock.builder();
//
//            if (cont.isParallel()) {
//                Integer poolSize = cont.getThreadPoolSize();
//                if (poolSize != null) {
//                    code.add("return container($L.class,$T.newFixedThreadPool($L))\n",
//                            cont.getInputType(), ClassName.get("java.util.concurrent", "Executors"), poolSize);
//                } else {
//                    code.add("return container($L.class,$T.newCachedThreadPool())\n",
//                            cont.getInputType(), ClassName.get("java.util.concurrent", "Executors"));
//                }
//            } else {
//                code.add("return container($L.class)\n", cont.getInputType());
//            }
//
//            for (SubLineType sub : cont.getSubLines().getSubLine()) {
//                BaseOperationType subOp = sub.getOperation().getValue();
//                if (sub.getCondition() != null) {
//                    code.add("    .withSubLine($L(),(input,ctx)->$L)\n",
//                            names.get(subOp), sub.getCondition().getExpression());
//                } else {
//                    code.add("    .withSubLine($L())\n", names.get(subOp));
//                }
//            }
//
//            if (cont.getReturnsFunction() != null) {
//                code.add("    .returns($L)\n", cont.getReturnsFunction().getExpression());
//            }
//
//            return code.add("    .build()").build();
//        }
//
//        private CodeBlock createIfElseCode(IfElseContainerType ifElse) {
//            CodeBlock.Builder code = CodeBlock.builder()
//                    .add("return ifElseContainer($L.class)\n", ifElse.getInputType());
//
//            for (ConditionalOperationType condOp : ifElse.getConditionalOperations().getConditionalOperation()) {
//                code.add("    .conditionally($L(),(input,ctx)->$L)\n",
//                        names.get(condOp.getOperation()), condOp.getCondition().getExpression());
//            }
//
//            if (ifElse.getElseOperation() != null) {
//                code.add("    .elseOp($L())\n", names.get(ifElse.getElseOperation()));
//            }
//
//            return code.add("    .build()").build();
//        }
//
//        private MethodSpec createConfigMethod(ConfigurationType config) {
//            CodeBlock.Builder code = CodeBlock.builder().add("return configuration()\n");
//
//            if (config.getOperationDefaultConfiguration() != null) {
//                code.add("    .stepDefaultConfiguration(operationConfiguration().build())\n");
//            }
//            if (config.getEventHandling() != null) {
//                code.add("    .eventHandlingDefinition(eventHandling().build())\n");
//            }
//            if (config.getPersistence() != null) {
//                code.add("    .persistence(persistenceConfiguration().persistenceType(PersistenceType.$L).build())\n",
//                        config.getPersistence().getPersistenceType());
//            }
//
//            return MethodSpec.methodBuilder("createConfiguration")
//                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
//                    .returns(ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration"))
//                    .addCode(code.add("    .build()").build())
//                    .build();
//        }
//
//        private void addParameters(ProcessingOperationType op, CodeBlock.Builder code) {
//            if (op.getParameters() == null) return;
//
//            for (Object param : op.getParameters().getValueParameterOrSupplierParameterOrContextParameter()) {
//                if (param instanceof ValueParameterType valueParameterType) {
//                    if ("java.lang.String".equals(valueParameterType.getValueType())) {
//                        code.add("    .parameter($L,$S)\n", valueParameterType.getRetriever(), valueParameterType.getValue());
//                    } else {
//                        code.add("    .parameter($L,$L)\n", valueParameterType.getRetriever(), valueParameterType.getValue());
//                    }
//                } else if (param instanceof SupplierParameterType supplierParameterType) {
//                    code.add("    .parameter($L,$L)\n", supplierParameterType.getRetriever(), supplierParameterType.getSupplier());
//                } else if (param instanceof ContextParameterType contextParameterType) {
//                    code.add("    .parameter($L,$L)\n", contextParameterType.getRetriever(), contextParameterType.getFunction());
//                }
//            }
//        }
//
//        private void addErrorHandling(ProcessingOperationType op, CodeBlock.Builder code) {
//            if (op.getOnErrors() == null) return;
//
//            for (Object error : op.getOnErrors().getSafeErrorOrUnsafeError()) {
//                SignalTypeEnum signalType = null;
//                if (error instanceof SafeErrorType) {
//                    signalType = ((SafeErrorType) error).getSignalType();
//                } else if (error instanceof UnsafeErrorType) {
//                    signalType = ((UnsafeErrorType) error).getSignalType();
//                }
//
//                if (signalType != null) {
//                    String throwableType = null;
//                    if (error instanceof SafeErrorType) {
//                        throwableType = ((SafeErrorType) error).getThrowableType();
//                    } else if (error instanceof UnsafeErrorType) {
//                        throwableType = ((UnsafeErrorType) error).getThrowableType();
//                    }
//
//                    String signal = switch (signalType) {
//                        case FATAL -> "fatal";
//                        case STOP -> "stop";
//                        case IGNORE -> "ignore";
//                    };
//
//                    code.add("    .onError($L($L.class)", signal, throwableType);
//
//                    ConditionType condition = null;
//                    if (error instanceof SafeErrorType) {
//                        condition = ((SafeErrorType) error).getCondition();
//                    } else if (error instanceof UnsafeErrorType) {
//                        condition = ((UnsafeErrorType) error).getCondition();
//                    }
//
//                    if (condition != null) {
//                        code.add(".condition((input,ctx)->$L)", condition.getExpression());
//                    }
//
//                    ActionType action = null;
//                    if (error instanceof SafeErrorType) {
//                        action = ((SafeErrorType) error).getAction();
//                    } else if (error instanceof UnsafeErrorType) {
//                        action = ((UnsafeErrorType) error).getAction();
//                    }
//
//                    if (action != null) {
//                        code.add(".action(()->$L)", action.getExpression());
//                    }
//
//                    code.add(".build())\n");
//                }
//            }}
//
//        private void addConditions(ProcessingOperationType op, CodeBlock.Builder code) {
//            if (op.getConditions() == null) return;
//
//            for (ConditionType condition : op.getConditions().getCondition()) {
//                code.add("    .conditional((input,ctx)->$L)\n", condition.getExpression());
//            }
//        }
//
//        private void addFallback(ProcessingOperationType op, CodeBlock.Builder code) {
//            if (op.getFallbackTransformer() == null) return;
//
//            String inputType = op.getInputType() != null ? op.getInputType().toLowerCase() : "input";
//            code.add("    .fallback(($L,ctx,exec)->$L)\n", inputType, op.getFallbackTransformer().getExpression());
//        }
//
//        private void addAccumulatorCollector(IteratorType iter, CodeBlock.Builder code) {
//            if (iter.getAccumulator() != null) {
//                String accType = switch (iter.getAccumulator().getType()) {
//                    case LIST -> "toList()";
//                    case SET -> "toSet()";
//                };
//                code.add("    .accumulator($L)\n", accType);
//            } else if (iter.getCollector() != null) {
//                code.add("    .collector($L)\n", iter.getCollector().getExpression());
//            }
//        }
//    }
//
//    private void assignNames(AssemblyLine assemblyLine) {
//        for (BaseOperationType op : assemblyLine.getOperations().getOperations()) {
//            assignName(op, "");
//        }
//    }
//
//    private void assignName(BaseOperationType op, String prefix) {
//        String id = op.getId();
//        String name = (id != null && !id.isEmpty()) ? cap(id) : cap(op.getClass().getSimpleName()) + (cnt++);
//        names.put(op, prefix.isEmpty() ? name : prefix + name);
//
//        if (op instanceof IteratorType iter) {
//            if (iter.getOperation() != null) {
//                assignName(iter.getOperation(), names.get(op) + "_");
//            }
//        } else if (op instanceof ContainerType cont) {
//            for (SubLineType sub : cont.getSubLines().getSubLine()) {
//                assignName(sub.getOperation().getValue(), names.get(op) + "_");
//            }
//        } else if (op instanceof IfElseContainerType ifElse) {
//            for (ConditionalOperationType condOp : ifElse.getConditionalOperations().getConditionalOperation()) {
//                assignName(condOp.getOperation(), names.get(op) + "_");
//            }
//            if (ifElse.getElseOperation() != null) {
//                assignName(ifElse.getElseOperation(), names.get(op) + "_");
//            }
//        }
//    }
//
//    private ClassName getReturnType(BaseOperationType op) {
//        ClassName className = null;
//        if (op instanceof  ProcessingOperationType processingOperationType) {
//            className = ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition");
//        } else if (op instanceof IteratorType) {
//            className = ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.IteratorDefinition");
//        } else if (op instanceof ContainerType) {
//            className = ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.ContainerBaseDefinition");
//        } else if (op instanceof IfElseContainerType) {
//            className = ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.UnvaryingIfElseContainerDefinition");
//        } else if (op instanceof SignalType) {
//            className = ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.SignalDefinition");
//        } else {
//            throw new IllegalArgumentException("Unknown operation type: " + op.getClass());
//        }
//        return className;
//    }
//
//    private String cap(String s) {
//        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
//    }
//
//    /**
//     * Unmarshal given content file to given type class.
//     *
//     * @param source the {@link InputStream} representing the XML Market
//     * @param type   the class type to unmarshal to
//     * @return the wanted jaxb object or null if an error was encountered while unmarshalling object.
//     * @throws JAXBException if an error occurs during unmarshalling
//     */
//    public static <T> T unmarshal(InputStream source, Class<T> type) throws JAXBException {
//        final JAXBContext jaxbContext = JAXBContext.newInstance(type);
//        final Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
//        return (T) unmarshaller.unmarshal(source);
//    }
//}