package io.github.gear4jtest.xml.generator;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class JavaImportManager {
    private final String packageName;
    private final Map<String, String> importsBySimpleName = new TreeMap<>();
    private final Set<String> staticImports = new TreeSet<>();

    JavaImportManager(String packageName) {
        this.packageName = packageName;
    }

    private static String packageNameOf(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot < 0 ? "" : fullyQualifiedName.substring(0, lastDot);
    }

    private static String simpleNameOf(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot < 0 ? fullyQualifiedName : fullyQualifiedName.substring(lastDot + 1);
    }

    String use(String fullyQualifiedName) {
        if (fullyQualifiedName == null || fullyQualifiedName.isBlank()) {
            return fullyQualifiedName;
        }
        if (!fullyQualifiedName.contains(".")) {
            return fullyQualifiedName;
        }

        String packagePart = packageNameOf(fullyQualifiedName);
        String simpleName = simpleNameOf(fullyQualifiedName);
        if ("java.lang".equals(packagePart) || packageName.equals(packagePart)) {
            return simpleName;
        }

        String alreadyRegistered = importsBySimpleName.get(simpleName);
        if (alreadyRegistered == null) {
            importsBySimpleName.put(simpleName, fullyQualifiedName);
            return simpleName;
        }
        if (alreadyRegistered.equals(fullyQualifiedName)) {
            return simpleName;
        }
        return fullyQualifiedName;
    }

    void addStatic(String fullyQualifiedMember) {
        staticImports.add(fullyQualifiedMember);
    }

    String renderImports() {
        StringBuilder builder = new StringBuilder();
        for (String imported : importsBySimpleName.values().stream().sorted().toList()) {
            builder.append("import ").append(imported).append(";\n");
        }
        if (!importsBySimpleName.isEmpty() && !staticImports.isEmpty()) {
            builder.append('\n');
        }
        for (String imported : staticImports) {
            builder.append("import static ").append(imported).append(";\n");
        }
        if (!importsBySimpleName.isEmpty() || !staticImports.isEmpty()) {
            builder.append('\n');
        }
        return builder.toString();
    }
}
