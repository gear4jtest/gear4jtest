package io.github.gear4jtest.xml.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import io.github.gear4jtest.xml.limit.XmlDefinitionBudget;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Action;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Configuration;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContextParameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Dependency;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ErrorHandler;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.EventHandling;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameters;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Persistence;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SupplierParameter;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Transformer;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ValueParameter;
import io.github.gear4jtest.xml.translator.XmlTranslationLimits;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;

public final class XmlAssemblyLineParser {
    private static final String CONDITION_ELEMENT = "condition";
    private static final String EXPRESSION_ATTRIBUTE = "expression";
    private static final String INPUT_TYPE_ATTRIBUTE = "inputType";
    private static final String OUTPUT_TYPE_ATTRIBUTE = "outputType";
    private static final String RETRIEVER_ATTRIBUTE = "retriever";

    public static final long DEFAULT_MAX_XML_BYTES = 2L * 1024L * 1024L;

    private final long maxXmlBytes;
    private final XmlTranslationLimits translationLimits;

    public XmlAssemblyLineParser() {
        this(DEFAULT_MAX_XML_BYTES, XmlTranslationLimits.defaults());
    }

    public XmlAssemblyLineParser(long maxXmlBytes) {
        this(maxXmlBytes, XmlTranslationLimits.defaults());
    }

    public XmlAssemblyLineParser(long maxXmlBytes, XmlTranslationLimits translationLimits) {
        if (maxXmlBytes <= 0) {
            throw new IllegalArgumentException("maxXmlBytes must be > 0");
        }
        this.maxXmlBytes = maxXmlBytes;
        this.translationLimits = java.util.Objects.requireNonNull(translationLimits,
                                                                  "translationLimits must not be null");
    }

    private static Element firstOperationChild(Element parent) {
        for (Element child : children(parent)) {
            String name = localName(child);
            if (CONDITION_ELEMENT.equals(name)) {
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

    public XmlAssemblyLineDefinition parse(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(ThrowingErrorHandler.INSTANCE);
            Document document = builder.parse(new BoundedInputStream(inputStream, maxXmlBytes));
            Element root = document.getDocumentElement();

            if (!"assemblyLine".equals(localName(root))) {
                throw new IllegalArgumentException(
                        "Expected root element <assemblyLine>, got <" + localName(root) + ">");
            }

            String id = required(root, "id");
            String inputType = required(root, INPUT_TYPE_ATTRIBUTE);
            String outputType = optional(root, OUTPUT_TYPE_ATTRIBUTE);

            Element operationsElement = requiredChild(root, "operations");
            XmlDefinitionBudget budget = new XmlDefinitionBudget(translationLimits);
            List<Operation> operations = new ArrayList<>();
            for (Element operation : children(operationsElement)) {
                operations.add(parseOperation(operation, budget, 1));
            }

            Configuration configuration = parseConfiguration(child(root, "configuration"));
            List<Dependency> dependencies = parseDependencies(child(root, "dependencies"), budget);

            return new XmlAssemblyLineDefinition(id, inputType, outputType, List.copyOf(operations), configuration,
                    dependencies);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException("Unable to parse Gear4J XML pipeline", e);
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maxBytes;
        private long count;

        private BoundedInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                increment(1);
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = delegate.read(b, off, len);
            if (read > 0) {
                increment(read);
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void increment(long bytes) throws IOException {
            count += bytes;
            if (count > maxBytes) {
                throw new IOException("Gear4J XML definition exceeds maxXmlBytes=" + maxBytes);
            }
        }
    }

    private Operation parseOperation(Element element, XmlDefinitionBudget budget, int depth) {
        String name = localName(element);
        if ("operation".equals(name)) {
            List<Element> nested = children(element);
            if (!nested.isEmpty()) {
                return parseOperation(nested.get(0), budget, depth);
            }
        }

        budget.recordOperation(depth);
        return switch (name) {
            case "processingOperation", "elseOperation" -> parseProcessing(element);
            case "operation" -> parseProcessing(element);
            case "iterator" -> parseIterator(element, budget, depth);
            case "container" -> parseContainer(element, budget, depth);
            case "ifElseContainer" -> parseIfElse(element, budget, depth);
            case "signal" -> parseSignal(element);
            default -> throw new IllegalArgumentException("Unsupported operation element: <" + name + ">");
        };
    }

    private ProcessingOperation parseProcessing(Element element) {
        return new ProcessingOperation(required(element, "id"), required(element, "type"),
                optional(element, INPUT_TYPE_ATTRIBUTE), parseParameters(child(element, "parameters")),
                parseErrors(child(element, "onErrors")), parseConditions(child(element, "conditions")),
                parseTransformer(child(element, "fallbackTransformer")));
    }

    private IteratorOperation parseIterator(Element element, XmlDefinitionBudget budget, int depth) {
        Element iterableFunction = requiredChild(element, "iterableFunction");
        Element operationWrapper = requiredChild(element, "operation");

        return new IteratorOperation(required(element, "id"), optional(element, INPUT_TYPE_ATTRIBUTE),
                optional(element, OUTPUT_TYPE_ATTRIBUTE), required(iterableFunction, EXPRESSION_ATTRIBUTE),
                parseOperation(operationWrapper, budget, depth + 1),
                child(element, "accumulator") == null ? null : required(child(element, "accumulator"), "type"),
                child(element, "collector") == null ? null
                        : required(child(element, "collector"), EXPRESSION_ATTRIBUTE));
    }

    private ContainerOperation parseContainer(Element element, XmlDefinitionBudget budget, int depth) {
        Element subLinesElement = requiredChild(element, "subLines");
        List<SubLine> subLines = new ArrayList<>();

        for (Element subLine : childrenNamed(subLinesElement, "subLine")) {
            Element operationElement = firstOperationChild(subLine);
            subLines.add(new SubLine(required(subLine, "id"), parseCondition(child(subLine, CONDITION_ELEMENT)),
                    parseOperation(operationElement, budget, depth + 1)));
        }

        Element returnsFunction = child(element, "returnsFunction");
        return new ContainerOperation(required(element, "id"), required(element, INPUT_TYPE_ATTRIBUTE),
                required(element, OUTPUT_TYPE_ATTRIBUTE),
                Boolean.parseBoolean(optionalOrDefault(element, "parallel", "false")),
                optionalInteger(element, "threadPoolSize"), List.copyOf(subLines),
                returnsFunction == null ? null : required(returnsFunction, EXPRESSION_ATTRIBUTE));
    }

    private IfElseOperation parseIfElse(Element element, XmlDefinitionBudget budget, int depth) {
        Element conditionalOperationsElement = requiredChild(element, "conditionalOperations");
        List<ConditionalOperation> conditionalOperations = new ArrayList<>();

        for (Element conditionalOperation : childrenNamed(conditionalOperationsElement, "conditionalOperation")) {
            Element op = firstOperationChild(conditionalOperation);
            Operation parsed = parseOperation(op, budget, depth + 1);
            if (!(parsed instanceof ProcessingOperation processingOperation)) {
                throw new IllegalArgumentException(
                        "ifElse conditionalOperation currently supports processing operations only");
            }
            conditionalOperations.add(new ConditionalOperation(required(conditionalOperation, "id"),
                    parseCondition(requiredChild(conditionalOperation, CONDITION_ELEMENT)), processingOperation));
        }

        ProcessingOperation elseOperation = null;
        Element elseElement = child(element, "elseOperation");
        if (elseElement != null) {
            elseOperation = (ProcessingOperation) parseOperation(elseElement, budget, depth + 1);
        }

        return new IfElseOperation(required(element, "id"), required(element, INPUT_TYPE_ATTRIBUTE),
                required(element, OUTPUT_TYPE_ATTRIBUTE), List.copyOf(conditionalOperations), elseOperation);
    }

    private SignalOperation parseSignal(Element element) {
        String type = required(element, "type").toUpperCase(Locale.ROOT);
        if (!"FATAL".equals(type) && !"STOP".equals(type)) {
            throw new IllegalArgumentException("Unsupported signal station type: " + type);
        }
        return new SignalOperation(required(element, "id"), type, optional(element, INPUT_TYPE_ATTRIBUTE),
                parseCondition(child(element, CONDITION_ELEMENT)));
    }

    private Parameters parseParameters(Element element) {
        if (element == null) {
            return new Parameters(List.of());
        }

        List<Parameter> parameters = new ArrayList<>();
        for (Element child : children(element)) {
            switch (localName(child)) {
                case "valueParameter" -> parameters.add(new ValueParameter(required(child, RETRIEVER_ATTRIBUTE),
                        required(child, "value"), optionalOrDefault(child, "valueType", "java.lang.String")));
                case "supplierParameter" ->
                    parameters.add(new SupplierParameter(required(child, RETRIEVER_ATTRIBUTE),
                            required(child, "supplier")));
                case "contextParameter" ->
                    parameters.add(new ContextParameter(required(child, RETRIEVER_ATTRIBUTE),
                            required(child, "function")));
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
                    parseCondition(child(child, CONDITION_ELEMENT)), parseAction(child(child, "action"))));
        }
        return List.copyOf(errors);
    }

    private List<Condition> parseConditions(Element element) {
        if (element == null) {
            return List.of();
        }
        List<Condition> conditions = new ArrayList<>();
        for (Element condition : childrenNamed(element, CONDITION_ELEMENT)) {
            conditions.add(parseCondition(condition));
        }
        return List.copyOf(conditions);
    }

    private Condition parseCondition(Element element) {
        if (element == null) {
            return null;
        }
        return new Condition(required(element, EXPRESSION_ATTRIBUTE),
                optionalOrDefault(element, "language", Condition.LANGUAGE_JAVA),
                optional(element, "description"));
    }

    private Action parseAction(Element element) {
        if (element == null) {
            return null;
        }
        return new Action(required(element, EXPRESSION_ATTRIBUTE), optional(element, "description"));
    }

    private Transformer parseTransformer(Element element) {
        if (element == null) {
            return null;
        }
        return new Transformer(required(element, EXPRESSION_ATTRIBUTE), optional(element, INPUT_TYPE_ATTRIBUTE),
                optional(element, OUTPUT_TYPE_ATTRIBUTE));
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
                    Boolean.parseBoolean(optionalOrDefault(persistenceElement, "storeResultObject", "true")),
                    optionalInteger(persistenceElement, "stationLogFlushThreshold"));
        }

        return new Configuration(eventHandling, persistence);
    }

    private List<Dependency> parseDependencies(Element element, XmlDefinitionBudget budget) {
        if (element == null) {
            return List.of();
        }
        List<Element> dependencyElements = childrenNamed(element, "dependency");
        budget.requireDependencies(dependencyElements.size());
        List<Dependency> dependencies = new ArrayList<>();
        for (Element dependency : dependencyElements) {
            dependencies.add(new Dependency(required(dependency, "name"), required(dependency, "type")));
        }
        return List.copyOf(dependencies);
    }

    private enum ThrowingErrorHandler implements org.xml.sax.ErrorHandler {
        INSTANCE;

        @Override
        public void warning(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXParseException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            throw exception;
        }
    }
}
