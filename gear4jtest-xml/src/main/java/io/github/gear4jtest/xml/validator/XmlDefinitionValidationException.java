package io.github.gear4jtest.xml.validator;

import java.util.Objects;

/**
 * Reports a semantic validation failure in an XML assembly-line definition.
 *
 * <p>
 * The path identifies the affected XML value so callers can return an
 * actionable diagnostic to definition authors before Java source generation.
 * </p>
 */
public final class XmlDefinitionValidationException extends IllegalArgumentException {
    private static final int MAX_RENDERED_VALUE_LENGTH = 160;

    private final String path;
    private final String rejectedValue;

    public XmlDefinitionValidationException(String path, String rejectedValue, String reason) {
        super(message(path, rejectedValue, reason));
        this.path = requireText(path, "path");
        this.rejectedValue = rejectedValue;
    }

    public XmlDefinitionValidationException(String path,
                                            String rejectedValue,
                                            String reason,
                                            Throwable cause) {
        super(message(path, rejectedValue, reason), cause);
        this.path = requireText(path, "path");
        this.rejectedValue = rejectedValue;
    }

    public String path() {
        return path;
    }

    public String rejectedValue() {
        return rejectedValue;
    }

    private static String message(String path, String value, String reason) {
        return "Invalid XML definition at " + requireText(path, "path") + " with value '" + renderValue(value)
                + "': " + requireText(reason, "reason");
    }

    private static String renderValue(String value) {
        if (value == null) {
            return "null";
        }
        String rendered = value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
        if (rendered.length() <= MAX_RENDERED_VALUE_LENGTH) {
            return rendered;
        }
        return rendered.substring(0, MAX_RENDERED_VALUE_LENGTH) + "...";
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
