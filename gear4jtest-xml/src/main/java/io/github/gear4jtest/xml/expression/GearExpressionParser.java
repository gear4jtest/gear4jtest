package io.github.gear4jtest.xml.expression;

import java.util.ArrayList;
import java.util.List;
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
    public static final int DEFAULT_MAX_EXPRESSION_LENGTH = 4_096;
    public static final int DEFAULT_MAX_TOKENS = 512;
    public static final int DEFAULT_MAX_PATH_SEGMENTS = 64;
    public static final int DEFAULT_MAX_NESTING_DEPTH = 64;

    private GearExpressionParser() {
    }

    public static GearExpression parse(String expression) {
        validateExpressionLength(expression);
        List<Token> tokens = tokenize(expression);
        validateTokenCount(tokens);
        Parser parser = new Parser(tokens);
        Node root = parser.expression();
        parser.expect(TokenType.EOF);
        return root::evaluate;
    }

    private static void validateExpressionLength(String expression) {
        if (expression != null && expression.length() > DEFAULT_MAX_EXPRESSION_LENGTH) {
            throw new GearExpressionException("Expression exceeds max length " + DEFAULT_MAX_EXPRESSION_LENGTH);
        }
    }

    private static void validateTokenCount(List<Token> tokens) {
        if (tokens.size() > DEFAULT_MAX_TOKENS) {
            throw new GearExpressionException("Expression exceeds max token count " + DEFAULT_MAX_TOKENS);
        }
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
                current = context.propertyAccessPolicy().readProperty(current, segments.get(i));
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
                case "==" -> safeEquals(left.evaluate(context), right.evaluate(context));
                case "!=" -> !safeEquals(left.evaluate(context), right.evaluate(context));
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

    private static boolean safeEquals(Object left, Object right) {
        if (left != null && !GearExpressionValues.isSafeScalar(left)) {
            throw unsafeEqualityOperand(left);
        }
        if (right != null && !GearExpressionValues.isSafeScalar(right)) {
            throw unsafeEqualityOperand(right);
        }
        return Objects.equals(left, right);
    }

    private static GearExpressionException unsafeEqualityOperand(Object value) {
        return new GearExpressionException("GEL equality only supports inert scalar values, not "
                + value.getClass().getName());
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int position;
        private int nestingDepth;

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
                return nestedExpression();
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
                if (segments.size() > DEFAULT_MAX_PATH_SEGMENTS) {
                    throw new GearExpressionException("Expression path exceeds max segments "
                            + DEFAULT_MAX_PATH_SEGMENTS);
                }
            }
            return new PathNode(List.copyOf(segments));
        }

        private Node nestedExpression() {
            nestingDepth++;
            if (nestingDepth > DEFAULT_MAX_NESTING_DEPTH) {
                throw new GearExpressionException("Expression exceeds max nesting depth "
                        + DEFAULT_MAX_NESTING_DEPTH);
            }
            try {
                Node nested = expression();
                expect(")");
                return nested;
            } finally {
                nestingDepth--;
            }
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
