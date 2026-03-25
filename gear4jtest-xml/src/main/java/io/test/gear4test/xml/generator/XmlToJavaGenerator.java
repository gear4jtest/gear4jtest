package io.test.gear4test.xml.generator;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import javax.xml.parsers.DocumentBuilderFactory;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlToJavaGenerator {
    private static final String BUILDERS = "io.github.gear4jtest.core.api.util.ElementModelBuilders";
    private final String packageName;
    private final String className;
    private int counter = 1;
    private final Set<String> generated = new HashSet<>();
    private final Map<String, TypeSpec> nestedClasses = new HashMap<>();

    public XmlToJavaGenerator(String pkg, String cls) {
        this.packageName = pkg;
        this.className = cls;
    }

    public JavaFile generateFromXml(File xmlPath) throws Exception {
        Document doc = parseXml(xmlPath);
        Element root = doc.getDocumentElement();

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        generateMainMethod(root, builder);
        generateOperations(root.getElementsByTagName("operations").item(0).getChildNodes(), builder);

        NodeList configs = root.getElementsByTagName("configuration");
        if (configs.getLength() > 0) {
            builder.addMethod(generateConfig((Element) configs.item(0)));
        }

        nestedClasses.values().forEach(builder::addType);

        return JavaFile.builder(packageName, builder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .addStaticImport(ClassName.bestGuess(BUILDERS), "*")
                .build();
    }

    private void generateMainMethod(Element root, TypeSpec.Builder builder) {
        String id = root.getAttribute("id");
        String input = root.getAttribute("inputType");

        MethodSpec.Builder method = MethodSpec.methodBuilder("create" + cap(id))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition"));

        CodeBlock.Builder code = CodeBlock.builder()
                .add("return $T.<$L>createAssemblyLine($S)\n", ClassName.bestGuess(BUILDERS), input, id);

        NodeList ops = root.getElementsByTagName("operations").item(0).getChildNodes();
        for (int i = 0; i < ops.getLength(); i++) {
            if (ops.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element op = (Element) ops.item(i);
                code.add("    .then($L())\n", getMethodName(op));
            }
        }

        if (root.getElementsByTagName("configuration").getLength() > 0) {
            code.add("    .configuration(createConfiguration())\n");
        }

        code.add("    .build()");
        builder.addMethod(method.addCode(code.build()).build());
    }

    private void generateOperations(NodeList ops, TypeSpec.Builder builder) {
        for (int i = 0; i < ops.getLength(); i++) {
            if (ops.item(i).getNodeType() == Node.ELEMENT_NODE) {
                generateOperation((Element) ops.item(i), builder);
            }
        }
    }

    private void generateOperation(Element op, TypeSpec.Builder builder) {
        String name = getMethodName(op);
        if (generated.contains(name)) return;
        generated.add(name);

        String tag = op.getTagName();
        switch (tag) {
            case "iterator":
            case "container":
            case "ifElseContainer":
                generateNestedOperation(op, builder);
                break;
            default:
                builder.addMethod(generateSimpleOperation(op));
        }

        generateChildOperations(op, builder);
    }

    private void generateNestedOperation(Element op, TypeSpec.Builder builder) {
        String tag = op.getTagName();
        String className = cap(tag) + counter++;
        TypeSpec.Builder nested = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC);

        generateChildOperations(op, nested);

        MethodSpec mainMethod = generateOperationMethod(op, className);
        nested.addMethod(mainMethod);

        nestedClasses.put(className, nested.build());

        String publicName = getMethodName(op);
        MethodSpec publicMethod = MethodSpec.methodBuilder(publicName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(getReturnType(tag))
                .addCode("return $L.$L();\n", className, mainMethod.name())
                .build();

        builder.addMethod(publicMethod);
    }

    private void generateChildOperations(Element op, TypeSpec.Builder builder) {
        String tag = op.getTagName();
        switch (tag) {
            case "iterator":
                NodeList iterOps = op.getElementsByTagName("operation");
                if (iterOps.getLength() > 0) {
                    generateOperations(iterOps.item(0).getChildNodes(), builder);
                }
                break;
            case "container":
                NodeList subLines = op.getElementsByTagName("subLine");
                for (int i = 0; i < subLines.getLength(); i++) {
                    Element sub = (Element) subLines.item(i);
                    for (int j = 0; j < sub.getChildNodes().getLength(); j++) {
                        Node child = sub.getChildNodes().item(j);
                        if (child.getNodeType() == Node.ELEMENT_NODE && !child.getNodeName().equals("condition")) {
                            generateOperation((Element) child, builder);
                        }
                    }
                }
                break;
            case "ifElseContainer":
                NodeList condOps = op.getElementsByTagName("conditionalOperation");
                for (int i = 0; i < condOps.getLength(); i++) {
                    Element cond = (Element) condOps.item(i);
                    NodeList ops = cond.getElementsByTagName("operation");
                    if (ops.getLength() > 0) {
                        generateOperation((Element) ops.item(0), builder);
                    }
                }
                NodeList elseOps = op.getElementsByTagName("elseOperation");
                if (elseOps.getLength() > 0) {
                    generateOperation((Element) elseOps.item(0), builder);
                }
                break;
        }
    }

    private MethodSpec generateOperationMethod(Element op, String className) {
        String tag = op.getTagName();
        String methodName = "create" + className;

        MethodSpec.Builder method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(getReturnType(tag));

        CodeBlock code = switch (tag) {
            case "iterator" -> generateIteratorCode(op);
            case "container" -> generateContainerCode(op);
            case "ifElseContainer" -> generateIfElseCode(op);
            default -> throw new IllegalArgumentException("Unsupported: " + tag);
        };

        return method.addCode(code).build();
    }

    private MethodSpec generateSimpleOperation(Element op) {
        String tag = op.getTagName();
        String name = getMethodName(op);

        MethodSpec.Builder method = MethodSpec.methodBuilder(name)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(getReturnType(tag));

        CodeBlock code = switch (tag) {
            case "processingOperation" -> generateProcessingCode(op);
            case "signal" -> generateSignalCode(op);
            default -> throw new IllegalArgumentException("Unsupported: " + tag);
        };

        return method.addCode(code).build();
    }

    private CodeBlock generateProcessingCode(Element op) {
        String id = op.getAttribute("id");
        String type = op.getAttribute("type");
        CodeBlock.Builder code = CodeBlock.builder()
                .add("return processingOperation($S,$L.class)\n", id, type);

        addParameters(op, code);
        addErrorHandling(op, code);
        addConditions(op, code);
        addFallbackTransformer(op, code);

        return code.add("    .build()").build();
    }

    private CodeBlock generateIteratorCode(Element op) {
        String id = op.getAttribute("id");
        CodeBlock.Builder code = CodeBlock.builder().add("return iterate($S)\n", id);

        NodeList iterFuncs = op.getElementsByTagName("iterableFunction");
        if (iterFuncs.getLength() > 0) {
            String expr = ((Element) iterFuncs.item(0)).getAttribute("expression");
            code.add("    .iterableFunction($L)\n", expr);
        }

        NodeList ops = op.getElementsByTagName("operation");
        if (ops.getLength() > 0) {
            String nested = createNestedOpsMethod((Element) ops.item(0));
            code.add("    .operation($L())\n", nested);
        }

        addAccumulatorCollector(op, code);
        return code.add("    .build()").build();
    }

    private CodeBlock generateContainerCode(Element op) {
        String input = op.getAttribute("inputType");
        boolean parallel = Boolean.parseBoolean(op.getAttribute("parallel"));

        CodeBlock.Builder code = CodeBlock.builder();
        if (parallel) {
            String pool = op.getAttribute("threadPoolSize");
            if (pool != null && !pool.isEmpty()) {
                code.add("return container($L.class,$T.newFixedThreadPool($L))\n",
                        input, ClassName.get("java.util.concurrent", "Executors"), pool);
            } else {
                code.add("return container($L.class,$T.newCachedThreadPool())\n",
                        input, ClassName.get("java.util.concurrent", "Executors"));
            }
        } else {
            code.add("return container($L.class)\n", input);
        }

        NodeList subLines = op.getElementsByTagName("subLine");
        for (int i = 0; i < subLines.getLength(); i++) {
            code.add(generateSubLineCode((Element) subLines.item(i)));
        }

        NodeList returns = op.getElementsByTagName("returnsFunction");
        if (returns.getLength() > 0) {
            String expr = ((Element) returns.item(0)).getAttribute("expression");
            code.add("    .returns($L)\n", expr);
        }

        return code.add("    .build()").build();
    }

    private CodeBlock generateIfElseCode(Element op) {
        String input = op.getAttribute("inputType");
        CodeBlock.Builder code = CodeBlock.builder().add("return ifElseContainer($L.class)\n", input);

        NodeList condOps = op.getElementsByTagName("conditionalOperation");
        for (int i = 0; i < condOps.getLength(); i++) {
            Element condOp = (Element) condOps.item(i);
            NodeList conds = condOp.getElementsByTagName("condition");
            NodeList ops = condOp.getElementsByTagName("operation");

            if (conds.getLength() > 0 && ops.getLength() > 0) {
                String condExpr = ((Element) conds.item(0)).getAttribute("expression");
                String opMethod = getMethodName((Element) ops.item(0));
                code.add("    .conditionally($L(),(input,ctx)->$L)\n", opMethod, condExpr);
            }
        }

        NodeList elseOps = op.getElementsByTagName("elseOperation");
        if (elseOps.getLength() > 0) {
            String elseMethod = getMethodName((Element) elseOps.item(0));
            code.add("    .elseOp($L())\n", elseMethod);
        }

        return code.add("    .build()").build();
    }

    private CodeBlock generateSignalCode(Element op) {
        String type = op.getAttribute("type");
        String input = op.getAttribute("inputType");

        CodeBlock.Builder code = CodeBlock.builder();
        switch (type) {
            case "FATAL" -> code.add("return fatalSignal($L.class)\n", input);
            case "STOP" -> code.add("return stopSignal($L.class)\n", input);
            case "IGNORE" -> code.add("return ignoreSignal($L.class)\n", input);
            default -> code.add("return fatalSignal($L.class)\n", input);
        }

        NodeList conds = op.getElementsByTagName("condition");
        if (conds.getLength() > 0) {
            String expr = ((Element) conds.item(0)).getAttribute("expression");
            code.add("    .condition(ctx->$L)\n", expr);
        }

        return code.add("    .build()").build();
    }

    private void addParameters(Element op, CodeBlock.Builder code) {
        NodeList params = op.getElementsByTagName("parameters");
        if (params.getLength() > 0) {
            NodeList list = params.item(0).getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element param = (Element) list.item(i);
                    String tag = param.getTagName();
                    String retriever = param.getAttribute("retriever");

                    switch (tag) {
                        case "valueParameter" -> {
                            String value = param.getAttribute("value");
                            String valueType = param.getAttribute("valueType");
                            if ("java.lang.String".equals(valueType)) {
                                code.add("    .parameter($L,$S)\n", retriever, value);
                            } else {
                                code.add("    .parameter($L,$L)\n", retriever, value);
                            }
                        }
                        case "supplierParameter" -> {
                            String supplier = param.getAttribute("supplier");
                            code.add("    .parameter($L,$L)\n", retriever, supplier);
                        }
                        case "contextParameter" -> {
                            String function = param.getAttribute("function");
                            code.add("    .parameter($L,$L)\n", retriever, function);
                        }
                    }
                }
            }
        }
    }

    private void addErrorHandling(Element op, CodeBlock.Builder code) {
        NodeList errors = op.getElementsByTagName("onErrors");
        if (errors.getLength() > 0) {
            NodeList list = errors.item(0).getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element error = (Element) list.item(i);
                    String signal = error.getAttribute("signalType");
                    String throwable = error.getAttribute("throwableType");

                    switch (signal) {
                        case "FATAL" -> code.add("    .onError(fatal($L.class)", throwable);
                        case "STOP" -> code.add("    .onError(stop($L.class)", throwable);
                        case "IGNORE" -> code.add("    .onError(ignore($L.class)", throwable);
                    }

                    NodeList conds = error.getElementsByTagName("condition");
                    if (conds.getLength() > 0) {
                        String expr = ((Element) conds.item(0)).getAttribute("expression");
                        code.add(".condition((input,ctx)->$L)", expr);
                    }

                    NodeList actions = error.getElementsByTagName("action");
                    if (actions.getLength() > 0) {
                        String expr = ((Element) actions.item(0)).getAttribute("expression");
                        code.add(".action(()->$L)", expr);
                    }

                    code.add(".build())\n");
                }
            }
        }
    }

    private void addConditions(Element op, CodeBlock.Builder code) {
        NodeList conds = op.getElementsByTagName("conditions");
        if (conds.getLength() > 0) {
            NodeList list = conds.item(0).getChildNodes();
            for (int i = 0; i < list.getLength(); i++) {
                if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element cond = (Element) list.item(i);
                    String expr = cond.getAttribute("expression");
                    code.add("    .conditional((input,ctx)->$L)\n", expr);
                }
            }
        }
    }

    private void addFallbackTransformer(Element op, CodeBlock.Builder code) {
        NodeList fallbacks = op.getElementsByTagName("fallbackTransformer");
        if (fallbacks.getLength() > 0) {
            Element fallback = (Element) fallbacks.item(0);
            String expr = fallback.getAttribute("expression");
            String input = op.getAttribute("inputType");
            code.add("    .fallback(($L,ctx,exec)->$L)\n",
                    input != null ? input.toLowerCase() : "input", expr);
        }
    }

    private void addAccumulatorCollector(Element op, CodeBlock.Builder code) {
        NodeList accs = op.getElementsByTagName("accumulator");
        NodeList colls = op.getElementsByTagName("collector");

        if (accs.getLength() > 0) {
            String type = ((Element) accs.item(0)).getAttribute("type");
            if ("LIST".equals(type)) {
                code.add("    .accumulator(toList())\n");
            } else if ("SET".equals(type)) {
                code.add("    .accumulator(toSet())\n");
            }
        } else if (colls.getLength() > 0) {
            String expr = ((Element) colls.item(0)).getAttribute("expression");
            code.add("    .collector($L)\n", expr);
        }
    }

    private CodeBlock generateSubLineCode(Element subLine) {
        CodeBlock.Builder code = CodeBlock.builder();
        Element opElement = null;

        for (int i = 0; i < subLine.getChildNodes().getLength(); i++) {
            Node node = subLine.getChildNodes().item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && !node.getNodeName().equals("condition")) {
                opElement = (Element) node;
                break;
            }
        }

        if (opElement != null) {
            String opMethod = getMethodName(opElement);
            NodeList conds = subLine.getElementsByTagName("condition");

            if (conds.getLength() > 0) {
                String expr = ((Element) conds.item(0)).getAttribute("expression");
                code.add("    .withSubLine($L(),(input,ctx)->$L)\n", opMethod, expr);
            } else {
                code.add("    .withSubLine($L())\n", opMethod);
            }
        }

        return code.build();
    }

    private MethodSpec generateConfig(Element config) {
        MethodSpec.Builder method = MethodSpec.methodBuilder("createConfiguration")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.AssemblyLineDefinition.Configuration"));

        CodeBlock.Builder code = CodeBlock.builder().add("return configuration()\n");

        if (config.getElementsByTagName("operationDefaultConfiguration").getLength() > 0) {
            code.add("    .stepDefaultConfiguration(operationConfiguration().build())\n");
        }

        if (config.getElementsByTagName("eventHandling").getLength() > 0) {
            code.add("    .eventHandlingDefinition(eventHandling().build())\n");
        }

        NodeList persist = config.getElementsByTagName("persistence");
        if (persist.getLength() > 0) {
            String type = ((Element) persist.item(0)).getAttribute("persistenceType");
            code.add("    .persistence(persistenceConfiguration().persistenceType(PersistenceType.$L).build())\n", type);
        }

        return method.addCode(code.add("    .build()").build()).build();
    }

    private String createNestedOpsMethod(Element container) {
        return "nestedOps" + (counter++);
    }

    private String getMethodName(Element op) {
        String id = op.getAttribute("id");
        String tag = op.getTagName();
        return (id != null && !id.isEmpty()) ? "create" + cap(id) : "create" + cap(tag) + (counter++);
    }

    private ClassName getReturnType(String tag) {
        return switch (tag) {
            case "processingOperation" -> ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.ProcessingOperationDefinition");
            case "iterator" -> ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.IteratorDefinition");
            case "container" -> ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.ContainerBaseDefinition");
            case "ifElseContainer" -> ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.UnvaryingIfElseContainerDefinition");
            case "signal" -> ClassName.bestGuess("io.github.gear4jtest.core.model.refactor.SignalDefinition");
            default -> throw new IllegalArgumentException("Unknown type: " + tag);
        };
    }

    private Document parseXml(File path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path);
    }

    private String cap(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
