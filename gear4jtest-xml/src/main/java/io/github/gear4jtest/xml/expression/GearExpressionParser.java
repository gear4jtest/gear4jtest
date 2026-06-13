package io.github.gear4jtest.xml.expression;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MVP parser for Gear Expression Language (GEL).
 * <p>
 * Supported constructs: literals, {@code input.foo} / {@code variables.foo}
 * property paths, {@code ==}, {@code !=}, {@code &&}, {@code ||}, {@code !} and
 * parentheses. It deliberately does not support Java method calls,
 * constructors, static access or class literals.
 */
public final class GearExpressionParser {
    private GearExpressionParser() {
    }

    public static GearExpression parse(String expression) {
        Parser parser = new Parser(tokenize(expression));
        Node root = parser.expression();
        parser.expect(TokenType.EOF);
        return context -> root.evaluate(context);
    }

    private static List<Token> tokenize(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new GearExpressionException("Expression must not be blank");
        }
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (Character.isJavaIdentifierStart(c)) {
                int start = i++;
                while (i < expression.length() && Character.isJavaIdentifierPart(expression.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(TokenType.IDENTIFIER, expression.substring(start, i)));
                continue;
            }
            if (Character.isDigit(c)) {
                int start = i++;
                while (i < expression.length()
                        && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
                    i++;
                }
                tokens.add(new Token(TokenType.NUMBER, expression.substring(start, i)));
                continue;
            }
            if (c == '\'' || c == '"') {
                char quote = c;
                int start = ++i;
                StringBuilder value = new StringBuilder();
                while (i < expression.length() && expression.charAt(i) != quote) {
                    char current = expression.charAt(i++);
                    if (current == '\\' && i < expression.length()) {
                        value.append(expression.charAt(i++));
                    } else {
                        value.append(current);
                    }
                }
                if (i >= expression.length()) {
                    throw new GearExpressionException("Unclosed string literal starting at " + start);
                }
                i++;
                tokens.add(new Token(TokenType.STRING, value.toString()));
                continue;
            }
            if (i + 1 < expression.length()) {
                String two = expression.substring(i, i + 2);
                if (two.equals("==") || two.equals("!=") || two.equals("&&") || two.equals("||")) {
                    tokens.add(new Token(TokenType.SYMBOL, two));
                    i += 2;
                    continue;
                }
            }
            if ("().!".indexOf(c) >= 0) {
                tokens.add(new Token(TokenType.SYMBOL, Character.toString(c)));
                i++;
                continue;
            }
            throw new GearExpressionException("Unsupported token at position " + i + ": " + c);
        }
        tokens.add(new Token(TokenType.EOF, "<eof>"));
        return tokens;
    }

    private interface Node {
        Object evaluate(GearExpressionContext context);
    }

    private record LiteralNode(Object value) implements Node {
        @Override
        public Object evaluate(GearExpressionContext context) {
            return value;
        }
    }

    private record PathNode(List<String> segments) implements Node {
        @Override
        public Object evaluate(GearExpressionContext context) {
            String root = segments.get(0);
            Object current;
            if ("input".equals(root)) {
                current = context.input();
            } else if ("variables".equals(root) || "context".equals(root)) {
                current = context.variables();
            } else {
                current = context.variables().get(root);
            }
            for (int i = 1; i < segments.size(); i++) {
                current = readProperty(current, segments.get(i));
            }
            return current;
        }
    }

    private record UnaryNode(String operator, Node child) implements Node {
        @Override
        public Object evaluate(GearExpressionContext context) {
            if ("!".equals(operator)) {
                return !truthy(child.evaluate(context));
            }
            throw new GearExpressionException("Unsupported unary operator: " + operator);
        }
    }

    private record BinaryNode(String operator, Node left, Node right) implements Node {
        @Override
        public Object evaluate(GearExpressionContext context) {
            return switch (operator) {
                case "&&" -> truthy(left.evaluate(context)) && truthy(right.evaluate(context));
                case "||" -> truthy(left.evaluate(context)) || truthy(right.evaluate(context));
                case "==" -> Objects.equals(left.evaluate(context), right.evaluate(context));
                case "!=" -> !Objects.equals(left.evaluate(context), right.evaluate(context));
                default -> throw new GearExpressionException("Unsupported binary operator: " + operator);
            };
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        throw new GearExpressionException("Expected boolean value but got " + value.getClass().getName());
    }

    private static Object readProperty(Object target, String property) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(property);
        }
        Class<?> type = target.getClass();
        Method accessor = findAccessor(type, property);
        try {
            return accessor.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new GearExpressionException("Unable to read property '" + property + "' from " + type.getName(), e);
        }
    }

    private static Method findAccessor(Class<?> type, String property) {
        for (RecordComponent component : type.getRecordComponents() == null ? new RecordComponent[0]
                : type.getRecordComponents()) {
            if (component.getName().equals(property)) {
                return component.getAccessor();
            }
        }
        String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String candidate : List.of(property, "get" + suffix, "is" + suffix)) {
            try {
                Method method = type.getMethod(candidate);
                if (method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new GearExpressionException("No readable property '" + property + "' on " + type.getName());
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int position;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        Node expression() {
            return or();
        }

        private Node or() {
            Node left = and();
            while (match("||")) {
                left = new BinaryNode("||", left, and());
            }
            return left;
        }

        private Node and() {
            Node left = equality();
            while (match("&&")) {
                left = new BinaryNode("&&", left, equality());
            }
            return left;
        }

        private Node equality() {
            Node left = unary();
            while (peek("==") || peek("!=")) {
                String operator = advance().value();
                left = new BinaryNode(operator, left, unary());
            }
            return left;
        }

        private Node unary() {
            if (match("!")) {
                return new UnaryNode("!", unary());
            }
            return primary();
        }

        private Node primary() {
            Token token = advance();
            if (token.type() == TokenType.STRING) {
                return new LiteralNode(token.value());
            }
            if (token.type() == TokenType.NUMBER) {
                return token.value().contains(".") ? new LiteralNode(Double.valueOf(token.value()))
                        : new LiteralNode(Long.valueOf(token.value()));
            }
            if (token.type() == TokenType.IDENTIFIER) {
                return identifier(token.value());
            }
            if ("(".equals(token.value())) {
                Node nested = expression();
                expect(")");
                return nested;
            }
            throw new GearExpressionException("Unexpected token: " + token.value());
        }

        private Node identifier(String first) {
            if ("true".equals(first)) {
                return new LiteralNode(Boolean.TRUE);
            }
            if ("false".equals(first)) {
                return new LiteralNode(Boolean.FALSE);
            }
            if ("null".equals(first)) {
                return new LiteralNode(null);
            }
            List<String> segments = new ArrayList<>();
            segments.add(first);
            while (match(".")) {
                Token next = expect(TokenType.IDENTIFIER);
                segments.add(next.value());
            }
            return new PathNode(List.copyOf(segments));
        }

        private boolean match(String value) {
            if (peek(value)) {
                position++;
                return true;
            }
            return false;
        }

        private boolean peek(String value) {
            return tokens.get(position).value().equals(value);
        }

        private Token advance() {
            return tokens.get(position++);
        }

        private Token expect(TokenType type) {
            Token token = advance();
            if (token.type() != type) {
                throw new GearExpressionException("Expected " + type + " but got " + token.value());
            }
            return token;
        }

        private void expect(String value) {
            if (!match(value)) {
                throw new GearExpressionException("Expected '" + value + "' but got " + tokens.get(position).value());
            }
        }
    }

    private record Token(TokenType type, String value) {}

    private enum TokenType {
        IDENTIFIER, NUMBER, STRING, SYMBOL, EOF
    }
}
