package io.github.gear4jtest.studio.runtime;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ChainDefinition.Choice;
import io.github.gear4jtest.studio.model.ChainDefinition.Operation;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.xml.validator.AssemblyLineValidator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Canonical semantic round-trip for the declared P0 subset. Unknown constructs
 * fail closed.
 */
public final class StudioXmlAdapter {
    public static final int MAX_BYTES = 65536;
    private static final String NS = "http://github.com/gear4jtest/core/model";
    private final OperationCatalog catalog;
    private final DefinitionChecks checks;

    public StudioXmlAdapter(OperationCatalog catalog) {
        this.catalog = catalog;
        checks = new DefinitionChecks(catalog);
    }

    public String write(ChainDefinition definition) {
        checks.safeToStore(definition);
        var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<assemblyLine xmlns=\"").append(NS)
                .append("\" id=\"").append(escape(definition.id()))
                .append("\" inputType=\"").append(escape(definition.inputType())).append("\" outputType=\"")
                .append(escape(definition.outputType())).append("\">\n  <operations>\n");
        for (var node : definition.nodes()) {
            if (node instanceof Operation operation)
                appendOperation(xml, operation, "processingOperation", "    ");
            else if (node instanceof Choice choice) {
                xml.append("    <ifElseContainer id=\"").append(escape(choice.id()))
                        .append("\" inputType=\"java.lang.String\" outputType=\"java.lang.String\">\n      <conditionalOperations>\n        <conditionalOperation id=\"")
                        .append(escape(choice.branchId()))
                        .append("\">\n          <condition language=\"gel\" expression=\"")
                        .append(escape(choice.condition())).append("\"/>\n");
                appendOperation(xml, choice.whenTrue(), "processingOperation", "          ");
                xml.append("        </conditionalOperation>\n      </conditionalOperations>\n");
                appendOperation(xml, choice.whenFalse(), "elseOperation", "      ");
                xml.append("    </ifElseContainer>\n");
            }
        }
        xml.append("  </operations>\n</assemblyLine>\n");
        if (xml.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw unsupported();
        return xml.toString();
    }

    private void appendOperation(StringBuilder xml, Operation operation, String tag, String indent) {
        var binding = catalog.find(operation.operationId());
        if (binding == null)
            throw unsupported();
        xml.append(indent).append('<').append(tag).append(" id=\"").append(escape(operation.id())).append("\" type=\"")
                .append(escape(operation.operationId())).append("\">\n");
        if (!operation.parameters().isEmpty()) {
            xml.append(indent).append("  <parameters>\n");
            new TreeMap<>(operation.parameters())
                    .forEach((name, value) -> xml.append(indent).append("    <valueParameter retriever=\"")
                            .append(escape(binding.retriever(name))).append("\" value=\"").append(escape(value.value()))
                            .append("\" valueType=\"java.lang.String\"/>\n"));
            xml.append(indent).append("  </parameters>\n");
        }
        xml.append(indent).append("</").append(tag).append(">\n");
    }

    public ChainDefinition read(String source) {
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw unsupported();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXParseException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXParseException {
                    throw e;
                }
            });
            var root = builder.parse(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
            require(root, "assemblyLine", Set.of("id", "inputType", "outputType"), Set.of("operations"));
            var operations = only(root, "operations");
            require(operations, "operations", Set.of(), Set.of("processingOperation", "ifElseContainer"));
            var nodes = new ArrayList<ChainDefinition.Node>();
            for (var element : children(operations))
                nodes.add(element.getLocalName().equals("processingOperation")
                        ? readOperation(element, "processingOperation") : readChoice(element));
            var definition = new ChainDefinition(1, attr(root, "id"), attr(root, "inputType"), attr(root, "outputType"),
                    nodes);
            checks.safeToStore(definition);
            new AssemblyLineValidator(MAX_BYTES).validate(source.getBytes(StandardCharsets.UTF_8));
            return definition;
        } catch (Exception e) {
            throw unsupported();
        }
    }

    private Choice readChoice(Element element) {
        require(element, "ifElseContainer", Set.of("id", "inputType", "outputType"),
                Set.of("conditionalOperations", "elseOperation"));
        if (!attr(element, "inputType").equals("java.lang.String")
                || !attr(element, "outputType").equals("java.lang.String"))
            throw unsupported();
        var conditions = only(element, "conditionalOperations");
        require(conditions, "conditionalOperations", Set.of(), Set.of("conditionalOperation"));
        var branch = only(conditions, "conditionalOperation");
        require(branch, "conditionalOperation", Set.of("id"), Set.of("condition", "processingOperation"));
        var condition = only(branch, "condition");
        require(condition, "condition", Set.of("language", "expression"), Set.of());
        if (!attr(condition, "language").equals("gel"))
            throw unsupported();
        return new Choice(attr(element, "id"), attr(branch, "id"), attr(condition, "expression"),
                readOperation(only(branch, "processingOperation"), "processingOperation"),
                readOperation(only(element, "elseOperation"), "elseOperation"));
    }

    private Operation readOperation(Element element, String tag) {
        require(element, tag, Set.of("id", "type", "inputType"), Set.of("parameters"));
        var binding = catalog.find(attr(element, "type"));
        if (binding == null)
            throw unsupported();
        if (element.hasAttribute("inputType") && !attr(element, "inputType").equals(binding.inputType().getName()))
            throw unsupported();
        var values = new TreeMap<String, ParameterValue>();
        if (!children(element).isEmpty()) {
            var parameters = only(element, "parameters");
            require(parameters, "parameters", Set.of(), Set.of("valueParameter"));
            for (var parameter : children(parameters)) {
                require(parameter, "valueParameter", Set.of("retriever", "value", "valueType"), Set.of());
                if (parameter.hasAttribute("valueType") && !attr(parameter, "valueType").equals("java.lang.String"))
                    throw unsupported();
                String name = binding.parameterGetters().keySet().stream()
                        .filter(key -> binding.retriever(key).equals(attr(parameter, "retriever"))).findFirst()
                        .orElseThrow(StudioXmlAdapter::unsupported);
                if (values.putIfAbsent(name, new ParameterValue(binding.descriptor().parameters().get(name).kind(),
                        attr(parameter, "value"))) != null)
                    throw unsupported();
            }
        }
        return new Operation(attr(element, "id"), binding.descriptor().id(), values);
    }

    private static void require(Element element, String tag, Set<String> attributes, Set<String> children) {
        if (!NS.equals(element.getNamespaceURI()) || !tag.equals(element.getLocalName()))
            throw unsupported();
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            var attribute = element.getAttributes().item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI()))
                continue;
            if (attribute.getNamespaceURI() != null || !attributes.contains(attribute.getNodeName()))
                throw unsupported();
        }
        for (var child : children(element))
            if (!NS.equals(child.getNamespaceURI()) || !children.contains(child.getLocalName()))
                throw unsupported();
    }

    private static List<Element> children(Element parent) {
        var result = new ArrayList<Element>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element)
                result.add(element);
            else if (node.getNodeType() != Node.COMMENT_NODE
                    && (node.getNodeType() != Node.TEXT_NODE || !node.getNodeValue().isBlank()))
                throw unsupported();
        }
        return result;
    }

    private static Element only(Element parent, String name) {
        var elements = children(parent).stream().filter(e -> name.equals(e.getLocalName())).toList();
        if (elements.size() != 1)
            throw unsupported();
        return elements.get(0);
    }

    private static String attr(Element element, String name) {
        if (!element.hasAttribute(name))
            throw unsupported();
        return element.getAttribute(name);
    }

    private static String escape(String value) {
        if (value.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF
                || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF)))
            throw unsupported();
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;").replace("\t", "&#9;")
                .replace("\n", "&#10;").replace("\r", "&#13;");
    }

    private static StudioProblem unsupported() {
        return new StudioProblem(StudioProblem.Code.UNSUPPORTED_FORMAT);
    }
}
