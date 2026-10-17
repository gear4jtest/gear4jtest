package io.github.gear4jtest.studio.runtime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Conservative lexical admission, independent of XML validity. Missing closing
 * tags and invalid Gear4J grammar are storable; ambiguous or unclassified data
 * are not. This scanner never repairs, serializes or executes its input.
 */
final class XmlSourceStoragePolicy {
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.:-]*");
    private static final Pattern ATTRIBUTE = Pattern.compile(
                                                             "([A-Za-z_][A-Za-z0-9_.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')",
                                                             Pattern.DOTALL);
    private static final Map<String, Set<String>> ATTRIBUTES = Map.ofEntries(
                                                                             Map.entry("assemblyLine",
                                                                                       Set.of("id", "inputType",
                                                                                              "outputType")),
                                                                             Map.entry("operations", Set.of()),
                                                                             Map.entry("processingOperation",
                                                                                       Set.of("id", "type",
                                                                                              "inputType")),
                                                                             Map.entry("elseOperation",
                                                                                       Set.of("id", "type",
                                                                                              "inputType")),
                                                                             Map.entry("ifElseContainer",
                                                                                       Set.of("id", "inputType",
                                                                                              "outputType")),
                                                                             Map.entry("conditionalOperations",
                                                                                       Set.of()),
                                                                             Map.entry("conditionalOperation",
                                                                                       Set.of("id")),
                                                                             Map.entry("condition",
                                                                                       Set.of("language",
                                                                                              "expression")),
                                                                             Map.entry("parameters", Set.of()),
                                                                             Map.entry("valueParameter",
                                                                                       Set.of("retriever", "value",
                                                                                              "valueType")));
    private final OperationCatalog catalog;

    XmlSourceStoragePolicy(OperationCatalog catalog) {
        this.catalog = catalog;
    }

    void check(String source) {
        if (source.getBytes(StandardCharsets.UTF_8).length > StudioXmlAdapter.MAX_BYTES)
            throw denied();
        var stack = new ArrayDeque<Scope>();
        int offset = 0;
        boolean ambiguous = false;
        while (offset < source.length()) {
            int open = source.indexOf('<', offset);
            if (open < 0) {
                if (!source.substring(offset).isBlank())
                    throw denied();
                break;
            }
            if (!source.substring(offset, open).isBlank())
                throw denied();
            if (source.startsWith("<!--", open)) {
                int end = source.indexOf("-->", open + 4);
                // Comments have no data classification in the manifest.
                if (end < 0 || !source.substring(open + 4, end).isBlank())
                    throw denied();
                offset = end + 3;
                continue;
            }
            if (source.startsWith("<?xml ", open)) {
                int end = source.indexOf("?>", open + 6);
                if (end < 0)
                    throw denied();
                var attributes = attributes(source.substring(open + 6, end));
                if (attributes.entrySet().stream().anyMatch(e -> !switch (e.getKey()) {
                    case "version" -> e.getValue().equals("1.0");
                    case "encoding" -> e.getValue().equalsIgnoreCase("UTF-8");
                    case "standalone" -> Set.of("yes", "no").contains(e.getValue());
                    default -> false;
                }))
                    throw denied();
                offset = end + 2;
                continue;
            }
            if (source.startsWith("<!", open) || source.startsWith("<?", open))
                throw denied();
            int end = endOfTag(source, open + 1);
            String token = source.substring(open + 1, end < 0 ? source.length() : end).strip();
            boolean closing = token.startsWith("/");
            if (closing)
                token = token.substring(1).strip();
            var name = NAME.matcher(token);
            if (!name.lookingAt()) {
                if (end < 0 && token.isEmpty())
                    break;
                throw denied();
            }
            String tag = name.group();
            String suffix = token.substring(name.end()).strip();
            if (closing) {
                if (!suffix.isEmpty())
                    throw denied();
                if (stack.isEmpty() || !stack.peek().tag().equals(tag))
                    ambiguous = true;
                else
                    stack.pop();
            } else {
                boolean selfClosing = suffix.endsWith("/");
                if (selfClosing)
                    suffix = suffix.substring(0, suffix.length() - 1);
                var values = attributes(suffix);
                var allowed = ATTRIBUTES.get(tag);
                if (allowed == null && !values.isEmpty())
                    throw denied();
                for (var entry : values.entrySet()) {
                    if (entry.getKey().equals("xmlns")) {
                        if (!entry.getValue().equals("http://github.com/gear4jtest/core/model"))
                            throw denied();
                    } else if (allowed == null || !allowed.contains(entry.getKey()))
                        throw denied();
                }
                RegisteredOperation operation = stack.isEmpty() ? null : stack.peek().operation();
                if (tag.equals("processingOperation") || tag.equals("elseOperation"))
                    operation = catalog.find(values.getOrDefault("type", ""));
                if (tag.equals("valueParameter") && values.containsKey("value")) {
                    if (ambiguous || operation == null)
                        throw denied();
                    parameter(operation, values);
                }
                if (!selfClosing)
                    stack.push(new Scope(tag, operation));
            }
            offset = end < 0 ? source.length() : end + 1;
        }
    }

    private static int endOfTag(String source, int from) {
        char quote = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == quote)
                    quote = 0;
            } else if (c == '\'' || c == '"')
                quote = c;
            else if (c == '>')
                return i;
        }
        if (quote != 0)
            throw denied();
        return -1;
    }

    private static Map<String, String> attributes(String text) {
        var values = new LinkedHashMap<String, String>();
        int at = 0;
        var matcher = ATTRIBUTE.matcher(text);
        while (at < text.length()) {
            if (Character.isWhitespace(text.charAt(at))) {
                at++;
                continue;
            }
            matcher.region(at, text.length());
            if (!matcher.lookingAt())
                throw denied();
            String value = decode(matcher.group(2) == null ? matcher.group(3) : matcher.group(2));
            if (value.length() > 4096 || values.putIfAbsent(matcher.group(1), value) != null)
                throw denied();
            at = matcher.end();
        }
        return values;
    }

    private static String decode(String text) {
        var result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '&') {
                result.append(c);
                continue;
            }
            int end = text.indexOf(';', i + 1);
            if (end < 0 || end - i > 12)
                throw denied();
            String entity = text.substring(i + 1, end);
            switch (entity) {
                case "amp" -> result.append('&');
                case "lt" -> result.append('<');
                case "gt" -> result.append('>');
                case "quot" -> result.append('"');
                case "apos" -> result.append('\'');
                default -> {
                    try {
                        if (!entity.startsWith("#"))
                            throw denied();
                        int code = entity.startsWith("#x") ? Integer.parseInt(entity.substring(2), 16)
                                : Integer.parseInt(entity.substring(1));
                        if (!Character.isValidCodePoint(code) || code >= 0xD800 && code <= 0xDFFF)
                            throw denied();
                        result.appendCodePoint(code);
                    } catch (IllegalArgumentException e) {
                        throw denied();
                    }
                }
            }
            i = end;
        }
        return result.toString();
    }

    private static void parameter(RegisteredOperation operation, Map<String, String> values) {
        String retriever = values.get("retriever");
        String name = operation.parameterGetters().keySet().stream()
                .filter(key -> operation.retriever(key).equals(retriever)).findFirst()
                .orElseThrow(XmlSourceStoragePolicy::denied);
        var descriptor = operation.descriptor().parameters().get(name);
        if (descriptor.kind() == ParameterValue.Kind.RESOURCE_REFERENCE
                && !descriptor.allowedReferences().contains(values.get("value")))
            throw denied();
    }

    private record Scope(String tag, RegisteredOperation operation) {}

    private static StudioProblem denied() {
        return new StudioProblem(StudioProblem.Code.SOURCE_NOT_ADMITTED);
    }
}
