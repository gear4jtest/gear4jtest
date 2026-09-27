package io.github.gear4jtest.studio.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy;
import io.github.gear4jtest.xml.generator.XmlJavaSourcePolicy;

/** Explicit allowlist; never scans the application's classpath. */
public final class OperationCatalog {
    private final Map<String, RegisteredOperation> registrations;
    private final String fingerprint;

    public OperationCatalog(List<RegisteredOperation> values) {
        var map = new TreeMap<String, RegisteredOperation>();
        for (var value : values)
            if (map.putIfAbsent(value.descriptor().id(), value) != null)
                throw new IllegalArgumentException("Duplicate operation");
        registrations = java.util.Collections.unmodifiableMap(map);
        var text = new StringBuilder();
        map.forEach((id, value) -> {
            append(text, id, value.type().getName(), value.inputType().getName(), value.outputType().getName(),
                   String.valueOf(value.descriptor().testAllowed()), value.descriptor().effects(),
                   value.descriptor().input().schemaId(), value.descriptor().output().schemaId(),
                   classHash(value.type()),
                   String.valueOf(value.descriptor().parameters().size()));
            new TreeMap<>(value.descriptor().parameters()).forEach((name, parameter) -> {
                append(text, name, parameter.kind().name(), String.valueOf(parameter.required()), value.retriever(name),
                       String.valueOf(parameter.allowedReferences().size()));
                parameter.allowedReferences().stream().sorted().forEach(ref -> append(text, ref));
            });
        });
        fingerprint = hash(text.toString());
    }

    public RegisteredOperation find(String id) {
        return registrations.get(id);
    }

    public List<OperationDescriptor> descriptors() {
        return registrations.values().stream().map(RegisteredOperation::descriptor).toList();
    }

    public String fingerprint() {
        return fingerprint;
    }

    XmlOperatorCapabilityPolicy capabilityPolicy() {
        var builder = XmlOperatorCapabilityPolicy.builder();
        registrations.forEach((id, value) -> {
            if (value.descriptor().testAllowed())
                builder.allow(id, value.type(), ExecutionMode.TEST);
        });
        return builder.build();
    }

    XmlJavaSourcePolicy sourcePolicy() {
        var allowed = registrations.values().stream()
                .flatMap(value -> value.parameterGetters().keySet().stream().map(value::retriever))
                .collect(java.util.stream.Collectors.toSet());
        return expression -> {
            if (!allowed.contains(expression))
                throw new SecurityException("Java expression denied");
        };
    }

    private static void append(StringBuilder target, String... values) {
        for (String value : values) {
            if (value == null)
                target.append("-1:");
            else
                target.append(value.length()).append(':').append(value);
        }
    }

    private static String classHash(Class<?> type) {
        String path = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = type.getResourceAsStream(path)) {
            if (stream == null)
                throw new IllegalArgumentException("Operation bytecode unavailable");
            byte[] bytes = stream.readNBytes(4 * 1024 * 1024 + 1);
            if (bytes.length > 4 * 1024 * 1024)
                throw new IllegalArgumentException("Operation bytecode exceeds limit");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.io.IOException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Operation fingerprint unavailable", e);
        }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
