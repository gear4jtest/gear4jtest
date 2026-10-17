package io.github.gear4jtest.studio.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.studio.model.DataContract;
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
            append(text, id, value.inputType().getName(), value.outputType().getName(),
                   String.valueOf(value.descriptor().testAllowed()), String.valueOf(value.descriptor().runAllowed()),
                   value.descriptor().effects(),
                   value.descriptor().input().toString(), value.descriptor().output().toString(),
                   String.valueOf(value.descriptor().parameters().size()));
            for (var mode : ExecutionMode.values()) {
                var binding = value.bindings().get(mode);
                if (binding != null) {
                    append(text, mode.name(), binding.type().getName(), classHash(binding.type()));
                    new TreeMap<>(binding.parameterGetters()).forEach((name, getter) -> append(text, name, getter));
                }
            }
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

    DataContract boundaryContract(String name) {
        for (var registration : registrations.values()) {
            for (var contract : List.of(registration.descriptor().input(), registration.descriptor().output())) {
                if (name.equals(contract.javaType()))
                    return new DataContract(name, null, DataContract.Origin.DECLARED, contract.complete(),
                            contract.assignableTo(), null);
            }
        }
        return null;
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
            value.bindings().forEach((mode, binding) -> builder.allow(id, binding.type(), mode));
        });
        return builder.build();
    }

    XmlJavaSourcePolicy sourcePolicy(ExecutionMode mode) {
        var allowed = registrations.values().stream()
                .map(value -> value.bindings().get(mode)).filter(java.util.Objects::nonNull)
                .flatMap(binding -> binding.parameterGetters().keySet().stream().map(binding::retriever))
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
