package io.github.gear4jtest.jdbc.migration;

import java.util.ArrayList;
import java.util.List;

final class SqlScriptSplitter {
    private SqlScriptSplitter() {
    }

    static List<String> split(String scriptContent) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;
        String dollarQuote = null;

        for (int i = 0; i < scriptContent.length(); i++) {
            char ch = scriptContent.charAt(i);
            char next = i + 1 < scriptContent.length() ? scriptContent.charAt(i + 1) : '\0';

            if (lineComment) {
                if (ch == '\n' || ch == '\r') {
                    lineComment = false;
                    current.append(ch);
                }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    blockComment = false;
                    current.append(' ');
                    i++;
                }
                continue;
            }
            if (dollarQuote != null) {
                if (scriptContent.startsWith(dollarQuote, i)) {
                    current.append(dollarQuote);
                    i += dollarQuote.length() - 1;
                    dollarQuote = null;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (singleQuoted) {
                current.append(ch);
                if (ch == '\'' && next == '\'') {
                    current.append(next);
                    i++;
                } else if (ch == '\'') {
                    singleQuoted = false;
                }
                continue;
            }
            if (doubleQuoted) {
                current.append(ch);
                if (ch == '"' && next == '"') {
                    current.append(next);
                    i++;
                } else if (ch == '"') {
                    doubleQuoted = false;
                }
                continue;
            }

            if (ch == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                singleQuoted = true;
                current.append(ch);
                continue;
            }
            if (ch == '"') {
                doubleQuoted = true;
                current.append(ch);
                continue;
            }
            if (ch == '$') {
                String tag = readDollarQuoteTag(scriptContent, i);
                if (tag != null) {
                    dollarQuote = tag;
                    current.append(tag);
                    i += tag.length() - 1;
                    continue;
                }
            }
            if (ch == ';') {
                addStatement(statements, current);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
    }

    private static String readDollarQuoteTag(String scriptContent, int start) {
        int end = scriptContent.indexOf('$', start + 1);
        if (end < 0) {
            return null;
        }
        for (int i = start + 1; i < end; i++) {
            char ch = scriptContent.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return null;
            }
        }
        return scriptContent.substring(start, end + 1);
    }
}
