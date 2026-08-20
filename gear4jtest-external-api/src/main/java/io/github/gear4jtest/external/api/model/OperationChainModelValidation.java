package io.github.gear4jtest.external.api.model;

import java.util.Locale;
import java.util.regex.Pattern;

final class OperationChainModelValidation {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private OperationChainModelValidation() {
    }

    static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " Unicode code points");
        }
        return value;
    }

    static String optionalText(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " Unicode code points");
        }
        return value;
    }

    static String requireSha256(String value) {
        if (value == null || !SHA_256_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("contentHash must be a 64-character hexadecimal SHA-256 value");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
