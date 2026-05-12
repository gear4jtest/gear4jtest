// package io.test.gear4test.xml.generator;
//
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.List;
// import java.util.Map;
// import java.util.Set;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;
// import java.util.stream.Collectors;
//
// import io.test.gear4jtest.xml.generated.DependencyType;
//
// public class EnhancedDependencyAnalyzer {
// private final Map<String, DependencyType> DependencyTypes = new HashMap<>();
// private final Set<String> detectedDependencies = new HashSet<>();
// private final Pattern objectPattern =
// Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.\\w+");
//
// /**
// * Enregistre les mappings de dépendances définis dans le XML
// */
// public void registerDependencyTypes(List<DependencyType> mappings) {
// mappings.forEach(mapping ->
// DependencyTypes.put(mapping.getName(), mapping));
// }
//
// /**
// * Analyse une expression et détecte les dépendances utilisées
// */
// public void analyzeExpression(String expression) {
// if (expression == null || expression.trim().isEmpty()) {
// return;
// }
//
// Matcher matcher = objectPattern.matcher(expression);
// while (matcher.find()) {
// String objectName = matcher.group(1);
//
// if (!isJavaKeyword(objectName) && !isLocalVariable(objectName)) {
// detectedDependencies.add(objectName);
// }
// }
// }
//
// /**
// * Retourne les dépendances détectées avec leurs mappings
// */
// public Set<ResolvedDependency> getResolvedDependencies() {
// return detectedDependencies.stream()
// .map(this::resolveDependency)
// .collect(Collectors.toSet());
// }
//
// /**
// * Retourne les dépendances non mappées (pour diagnostic)
// */
// public Set<String> getUnmappedDependencies() {
// return detectedDependencies.stream()
// .filter(dep -> !DependencyTypes.containsKey(dep))
// .collect(Collectors.toSet());
// }
//
// private ResolvedDependency resolveDependency(String dependencyName) {
// DependencyType mapping = DependencyTypes.get(dependencyName);
//
// if (mapping != null) {
// return new ResolvedDependency(
// dependencyName,
// mapping.getType(),
// mapping.getType().substring(mapping.getType().lastIndexOf('.') + 1),
// true
// );
// } else {
// throw new IllegalArgumentException("Cannot infer type for dependency: " +
// dependencyName);
// }
// }
//
// private boolean isJavaKeyword(String word) {
// return Set.of("this", "super", "class", "int", "long", "double", "float",
// "boolean",
// "String", "Object", "System", "Math", "ctx", "input", "exec").contains(word);
// }
//
// private boolean isLocalVariable(String word) {
// return Set.of("ctx", "input", "exec", "e", "exception",
// "result").contains(word);
// }
//
// private String capitalize(String str) {
// if (str == null || str.isEmpty()) return str;
// return str.substring(0, 1).toUpperCase() + str.substring(1);
// }
// }
