package io.test.gear4jtest.xml.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;

import io.test.gear4jtest.xml.model.XmlPipelineDefinition;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Action;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ConditionalOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Configuration;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ContextParameter;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Dependency;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ErrorHandler;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.EventHandling;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Parameter;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Parameters;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Persistence;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SubLine;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SupplierParameter;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.Transformer;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ValueParameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class XmlPipelineParser {
    private static Element firstOperationChild(Element parent) {
        for (Element child : children(parent)) {
            String name = localName(child);
            if ("condition".equals(name)) {
                continue;
            }
            return child;
        }
        throw new IllegalArgumentException("No operation child found in <" + localName(parent) + ">");
    }

    private static Element requiredChild(Element parent, String name) {
        Element child = child(parent, name);
        if (child == null) {
            throw new IllegalArgumentException("Missing required child <" + name + "> in <" + localName(parent) + ">");
        }
        return child;
    }

    private static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Element child : children(parent)) {
            if (name.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> childrenNamed(Element parent, String name) {
        return children(parent).stream()
                .filter(child -> name.equals(localName(child)))
                .toList();
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        if (parent == null) {
            return elements;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static String required(Element element, String attribute) {
        String value = optional(element, attribute);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required attribute '" + attribute + "' on <" + localName(element) + ">");
        }
        return value;
    }

    private static String optionalOrDefault(Element element, String attribute, String defaultValue) {
        String value = optional(element, attribute);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String optional(Element element, String attribute) {
        return hasAttribute(element, attribute) ? element.getAttribute(attribute) : null;
    }

    private static Integer optionalInteger(Element element, String attribute) {
        String value = optional(element, attribute);
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private static boolean hasAttribute(Element element, String attribute) {
        return element.hasAttribute(attribute);
    }

    private static String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
    }

    public XmlPipelineDefinition parse(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory.newDocumentBuilder().parse(inputStream);
            Element root = document.getDocumentElement();

            if (!"assemblyLine".equals(localName(root))) {
                throw new IllegalArgumentException(
                        "Expected root element <assemblyLine>, got <" + localName(root) + ">");
            }

            String id = required(root, "id");
            String inputType = required(root, "inputType");
            String outputType = optional(root, "outputType");

            Element operationsElement = requiredChild(root, "operations");
            List<Operation> operations = children(operationsElement).stream()
                    .map(this::parseOperation)
                    .toList();

            Configuration configuration = parseConfiguration(child(root, "configuration"));
            List<Dependency> dependencies = parseDependencies(child(root, "dependencies"));

            return new XmlPipelineDefinition(id, inputType, outputType, operations, configuration, dependencies);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Unable to parse Gear4J XML pipeline", e);
        }
    }

    private Operation parseOperation(Element element) {
        String name = localName(element);
        if ("operation".equals(name)) {
            List<Element> nested = children(element);
            if (!nested.isEmpty()) {
                return parseOperation(nested.get(0));
            }
            if (hasAttribute(element, "type")) {
                return parseProcessing(element);
            }
        }

        return switch (name) {
            case "processingOperation", "elseOperation" -> parseProcessing(element);
            case "iterator" -> parseIterator(element);
            case "container" -> parseContainer(element);
            case "ifElseContainer" -> parseIfElse(element);
            case "signal" -> parseSignal(element);
            default -> throw new IllegalArgumentException("Unsupported operation element: <" + name + ">");
        };
    }

    private ProcessingOperation parseProcessing(Element element) {
        return new ProcessingOperation(required(element, "id"), required(element, "type"),
                optional(element, "inputType"), parseParameters(child(element, "parameters")),
                parseErrors(child(element, "onErrors")), parseConditions(child(element, "conditions")),
                parseTransformer(child(element, "fallbackTransformer")));
    }

    private IteratorOperation parseIterator(Element element) {
        Element iterableFunction = requiredChild(element, "iterableFunction");
        Element operationWrapper = requiredChild(element, "operation");

        return new IteratorOperation(required(element, "id"), optional(element, "inputType"),
                optional(element, "outputType"), required(iterableFunction, "expression"),
                parseOperation(operationWrapper),
                child(element, "accumulator") == null ? null : required(child(element, "accumulator"), "type"),
                child(element, "collector") == null ? null : required(child(element, "collector"), "expression"));
    }

    private ContainerOperation parseContainer(Element element) {
        Element subLinesElement = requiredChild(element, "subLines");
        List<SubLine> subLines = new ArrayList<>();

        for (Element subLine : childrenNamed(subLinesElement, "subLine")) {
            Element operationElement = firstOperationChild(subLine);
            subLines.add(new SubLine(optional(subLine, "id"), parseCondition(child(subLine, "condition")),
                    parseOperation(operationElement)));
        }

        Element returnsFunction = child(element, "returnsFunction");
        return new ContainerOperation(required(element, "id"), required(element, "inputType"),
                required(element, "outputType"), Boolean.parseBoolean(optionalOrDefault(element, "parallel", "false")),
                optionalInteger(element, "threadPoolSize"), List.copyOf(subLines),
                returnsFunction == null ? null : required(returnsFunction, "expression"));
    }

    private IfElseOperation parseIfElse(Element element) {
        Element conditionalOperationsElement = requiredChild(element, "conditionalOperations");
        List<ConditionalOperation> conditionalOperations = new ArrayList<>();

        for (Element conditionalOperation : childrenNamed(conditionalOperationsElement, "conditionalOperation")) {
            Element op = firstOperationChild(conditionalOperation);
            Operation parsed = parseOperation(op);
            if (!(parsed instanceof ProcessingOperation processingOperation)) {
                throw new IllegalArgumentException(
                        "ifElse conditionalOperation currently supports processing operations only");
            }
            conditionalOperations.add(new ConditionalOperation(
                    parseCondition(requiredChild(conditionalOperation, "condition")), processingOperation));
        }

        ProcessingOperation elseOperation = null;
        Element elseElement = child(element, "elseOperation");
        if (elseElement != null) {
            elseOperation = parseProcessing(elseElement);
        }

        return new IfElseOperation(required(element, "id"), required(element, "inputType"),
                required(element, "outputType"), List.copyOf(conditionalOperations), elseOperation);
    }

    private SignalOperation parseSignal(Element element) {
        return new SignalOperation(required(element, "id"), required(element, "type").toUpperCase(Locale.ROOT),
                optional(element, "inputType"), parseCondition(child(element, "condition")));
    }

    private Parameters parseParameters(Element element) {
        if (element == null) {
            return new Parameters(List.of());
        }

        List<Parameter> parameters = new ArrayList<>();
        for (Element child : children(element)) {
            switch (localName(child)) {
                case "valueParameter" -> parameters.add(new ValueParameter(required(child, "retriever"),
                        required(child, "value"), optionalOrDefault(child, "valueType", "java.lang.String")));
                case "supplierParameter" ->
                    parameters.add(new SupplierParameter(required(child, "retriever"), required(child, "supplier")));
                case "contextParameter" ->
                    parameters.add(new ContextParameter(required(child, "retriever"), required(child, "function")));
                default ->
                    throw new IllegalArgumentException("Unsupported parameter element: <" + localName(child) + ">");
            }
        }
        return new Parameters(List.copyOf(parameters));
    }

    private List<ErrorHandler> parseErrors(Element element) {
        if (element == null) {
            return List.of();
        }

        List<ErrorHandler> errors = new ArrayList<>();
        for (Element child : children(element)) {
            String name = localName(child);
            if (!"safeError".equals(name) && !"unsafeError".equals(name)) {
                throw new IllegalArgumentException("Unsupported error handler element: <" + name + ">");
            }
            errors.add(new ErrorHandler("safeError".equals(name),
                    required(child, "signalType").toUpperCase(Locale.ROOT), required(child, "throwableType"),
                    parseCondition(child(child, "condition")), parseAction(child(child, "action"))));
        }
        return List.copyOf(errors);
    }

    private List<Condition> parseConditions(Element element) {
        if (element == null) {
            return List.of();
        }
        List<Condition> conditions = new ArrayList<>();
        for (Element condition : childrenNamed(element, "condition")) {
            conditions.add(parseCondition(condition));
        }
        return List.copyOf(conditions);
    }

    private Condition parseCondition(Element element) {
        if (element == null) {
            return null;
        }
        return new Condition(required(element, "expression"), optional(element, "description"));
    }

    private Action parseAction(Element element) {
        if (element == null) {
            return null;
        }
        return new Action(required(element, "expression"), optional(element, "description"));
    }

    private Transformer parseTransformer(Element element) {
        if (element == null) {
            return null;
        }
        return new Transformer(required(element, "expression"), optional(element, "inputType"),
                optional(element, "outputType"));
    }

    private Configuration parseConfiguration(Element element) {
        if (element == null) {
            return null;
        }
        EventHandling eventHandling = null;
        Element eventHandlingElement = child(element, "eventHandling");
        if (eventHandlingElement != null) {
            Element global = child(eventHandlingElement, "globalEventConfiguration");
            eventHandling = new EventHandling(global == null ? null
                    : Boolean.parseBoolean(optionalOrDefault(global, "eventOnParameterChanged", "false")));
        }

        Persistence persistence = null;
        Element persistenceElement = child(element, "persistence");
        if (persistenceElement != null) {
            persistence = new Persistence(
                    Boolean.parseBoolean(optionalOrDefault(persistenceElement, "storeResultObject", "true")));
        }

        return new Configuration(eventHandling, persistence);
    }

    private List<Dependency> parseDependencies(Element element) {
        if (element == null) {
            return List.of();
        }
        List<Dependency> dependencies = new ArrayList<>();
        for (Element dependency : childrenNamed(element, "dependency")) {
            dependencies.add(new Dependency(required(dependency, "name"), required(dependency, "type")));
        }
        return List.copyOf(dependencies);
    }
}
