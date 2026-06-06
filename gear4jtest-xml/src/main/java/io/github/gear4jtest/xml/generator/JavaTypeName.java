package io.github.gear4jtest.xml.generator;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

final class JavaTypeName {
    static final JavaTypeName OBJECT = raw("java.lang.Object");
    static final JavaTypeName STRING = raw("java.lang.String");
    private final String rawType;
    private final List<JavaTypeName> arguments;
    private final boolean wildcard;
    private final boolean extendsWildcard;
    private final boolean superWildcard;

    private JavaTypeName(String rawType,
                         List<JavaTypeName> arguments,
                         boolean wildcard,
                         boolean extendsWildcard,
                         boolean superWildcard) {
        this.rawType = Objects.requireNonNull(rawType, "rawType");
        this.arguments = List.copyOf(arguments);
        this.wildcard = wildcard;
        this.extendsWildcard = extendsWildcard;
        this.superWildcard = superWildcard;
    }

    static JavaTypeName raw(String rawType) {
        return new JavaTypeName(rawType, List.of(), false, false, false);
    }

    static JavaTypeName parameterized(String rawType, JavaTypeName... arguments) {
        return new JavaTypeName(rawType, List.of(arguments), false, false, false);
    }

    static JavaTypeName parse(String value) {
        if (value == null || value.isBlank()) {
            return OBJECT;
        }
        return new Parser(value.trim()).parseType();
    }

    static JavaTypeName from(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return raw(clazz.getComponentType().getCanonicalName() + "[]");
            }
            return raw(clazz.getCanonicalName());
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type raw = parameterizedType.getRawType();
            if (!(raw instanceof Class<?> rawClass)) {
                return OBJECT;
            }
            List<JavaTypeName> args = new ArrayList<>();
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                args.add(from(argument));
            }
            return new JavaTypeName(rawClass.getCanonicalName(), args, false, false, false);
        }
        if (type instanceof TypeVariable<?>) {
            return OBJECT;
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] lowerBounds = wildcardType.getLowerBounds();
            if (lowerBounds.length > 0) {
                return new JavaTypeName(from(lowerBounds[0]).rawType, from(lowerBounds[0]).arguments, true, false,
                        true);
            }
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0 && !Object.class.equals(upperBounds[0])) {
                JavaTypeName bound = from(upperBounds[0]);
                return new JavaTypeName(bound.rawType, bound.arguments, true, true, false);
            }
            return new JavaTypeName("java.lang.Object", List.of(), true, false, false);
        }
        if (type instanceof GenericArrayType genericArrayType) {
            return raw(from(genericArrayType.getGenericComponentType()).canonical() + "[]");
        }
        return OBJECT;
    }

    String rawType() {
        return rawType;
    }

    List<JavaTypeName> arguments() {
        return arguments;
    }

    JavaTypeName erase() {
        return raw(rawType);
    }

    String canonical() {
        if (arguments.isEmpty()) {
            return rawType;
        }
        StringJoiner joiner = new StringJoiner(", ", rawType + "<", ">");
        for (JavaTypeName argument : arguments) {
            joiner.add(argument.canonical());
        }
        return joiner.toString();
    }

    String render(JavaImportManager imports) {
        if (wildcard) {
            if (superWildcard) {
                return "? super " + renderNonWildcard(imports);
            }
            if (extendsWildcard) {
                return "? extends " + renderNonWildcard(imports);
            }
            return "?";
        }
        return renderNonWildcard(imports);
    }

    String renderClassLiteral(JavaImportManager imports) {
        return erase().render(imports) + ".class";
    }

    boolean isIterableLike() {
        return "java.lang.Iterable".equals(rawType) || "java.util.Collection".equals(rawType)
                || "java.util.List".equals(rawType) || "java.util.Set".equals(rawType);
    }

    JavaTypeName firstArgumentOrObject() {
        return arguments.isEmpty() ? OBJECT : arguments.get(0);
    }

    private String renderNonWildcard(JavaImportManager imports) {
        String base;
        if (rawType.endsWith("[]")) {
            JavaTypeName component = raw(rawType.substring(0, rawType.length() - 2));
            base = component.render(imports) + "[]";
        } else {
            base = imports.use(rawType);
        }
        if (arguments.isEmpty()) {
            return base;
        }
        StringJoiner joiner = new StringJoiner(", ", base + "<", ">");
        for (JavaTypeName argument : arguments) {
            joiner.add(argument.render(imports));
        }
        return joiner.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaTypeName that)) {
            return false;
        }
        return wildcard == that.wildcard && extendsWildcard == that.extendsWildcard
                && superWildcard == that.superWildcard && rawType.equals(that.rawType)
                && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawType, arguments, wildcard, extendsWildcard, superWildcard);
    }

    private static final class Parser {
        private final String value;
        private int index;

        private Parser(String value) {
            this.value = value;
        }

        private static String normalize(String raw) {
            return switch (raw) {
                case "String" -> "java.lang.String";
                case "Integer" -> "java.lang.Integer";
                case "Long" -> "java.lang.Long";
                case "Boolean" -> "java.lang.Boolean";
                case "Double" -> "java.lang.Double";
                case "Float" -> "java.lang.Float";
                case "Short" -> "java.lang.Short";
                case "Byte" -> "java.lang.Byte";
                case "Character" -> "java.lang.Character";
                case "Object" -> "java.lang.Object";
                case "List" -> "java.util.List";
                case "Set" -> "java.util.Set";
                case "Map" -> "java.util.Map";
                default -> raw;
            };
        }

        JavaTypeName parseType() {
            skipWhitespace();
            String raw = readRawType();
            skipWhitespace();
            if (!peek('<')) {
                return JavaTypeName.raw(normalize(raw));
            }
            index++;
            List<JavaTypeName> args = new ArrayList<>();
            do {
                skipWhitespace();
                args.add(parseType());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                } else {
                    break;
                }
            } while (true);
            expect('>');
            return new JavaTypeName(normalize(raw), args, false, false, false);
        }

        private String readRawType() {
            int start = index;
            while (index < value.length()) {
                char c = value.charAt(index);
                if (Character.isJavaIdentifierPart(c) || c == '.' || c == '$' || c == '[' || c == ']') {
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) {
                throw new IllegalArgumentException("Invalid Java type: " + value);
            }
            return value.substring(start, index);
        }

        private boolean peek(char expected) {
            return index < value.length() && value.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (!peek(expected)) {
                throw new IllegalArgumentException("Expected '" + expected + "' in Java type: " + value);
            }
            index++;
        }

        private void skipWhitespace() {
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
        }
    }
}
