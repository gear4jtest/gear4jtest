package io.github.gear4jtest.core.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;

final class PublishedApiBoundaryAnalyzer {
    private static final Set<String> API_MARKERS = Set.of("PublicApi", "Spi");
    private static final Pattern JAVA_TYPE_TOKEN = Pattern.compile(
                                                                   "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
                                                                           + "(?:\\.\\p{javaJavaIdentifierStart}"
                                                                           + "\\p{javaJavaIdentifierPart}*)*");

    private PublishedApiBoundaryAnalyzer() {
    }

    static List<String> findViolations(Path repositoryRoot, List<String> modules) throws IOException {
        List<Path> sourceFiles = productionSources(repositoryRoot, modules);
        List<SourceUnit> sourceUnits = parse(sourceFiles);
        Map<String, String> packageMarkers = packageMarkers(sourceUnits);
        InternalCatalog internalCatalog = internalCatalog(sourceUnits, packageMarkers);
        Set<String> violations = new LinkedHashSet<>();

        for (SourceUnit sourceUnit : sourceUnits) {
            String packageName = packageName(sourceUnit.compilationUnit());
            if (!API_MARKERS.contains(packageMarkers.get(packageName))
                    || sourceUnit.path().getFileName().toString().equals("package-info.java")) {
                continue;
            }

            SignatureInspector inspector = new SignatureInspector(repositoryRoot,
                    sourceUnit,
                    internalCatalog,
                    violations);
            for (Tree typeDeclaration : sourceUnit.compilationUnit().getTypeDecls()) {
                if (typeDeclaration instanceof ClassTree classTree) {
                    inspector.inspectTopLevelType(classTree);
                }
            }
        }

        return violations.stream().sorted().toList();
    }

    private static List<Path> productionSources(Path repositoryRoot, List<String> modules) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : modules) {
            Path sourceRoot = repositoryRoot.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sourceRoot)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .forEach(sources::add);
            }
        }
        sources.sort(Comparator.naturalOrder());
        return sources;
    }

    private static List<SourceUnit> parse(List<Path> sourceFiles) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK compiler is required to verify published API signatures");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                                                                                   diagnostics,
                                                                                   Locale.ROOT,
                                                                                   StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> javaFiles = fileManager.getJavaFileObjectsFromFiles(
                                                                                                   sourceFiles.stream()
                                                                                                           .map(Path::toFile)
                                                                                                           .toList());
            JavacTask task = (JavacTask) compiler.getTask(
                                                          null,
                                                          fileManager,
                                                          diagnostics,
                                                          List.of("-proc:none", "--release", "17"),
                                                          null,
                                                          javaFiles);
            List<SourceUnit> sourceUnits = new ArrayList<>();
            for (CompilationUnitTree compilationUnit : task.parse()) {
                Path sourcePath = Path.of(compilationUnit.getSourceFile().toUri()).toAbsolutePath().normalize();
                sourceUnits.add(new SourceUnit(sourcePath, compilationUnit));
            }

            List<String> parseErrors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(PublishedApiBoundaryAnalyzer::formatDiagnostic)
                    .toList();
            if (!parseErrors.isEmpty()) {
                throw new IllegalStateException("Unable to parse production sources:\n - "
                        + String.join("\n - ", parseErrors));
            }
            return sourceUnits;
        }
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        String source = diagnostic.getSource() == null
                ? "<unknown>"
                : Path.of(diagnostic.getSource().toUri()).getFileName().toString();
        return source + ":" + diagnostic.getLineNumber() + ": "
                + diagnostic.getMessage(Locale.ROOT);
    }

    private static Map<String, String> packageMarkers(List<SourceUnit> sourceUnits) throws IOException {
        Map<String, String> markers = new HashMap<>();
        for (SourceUnit sourceUnit : sourceUnits) {
            if (!sourceUnit.path().getFileName().toString().equals("package-info.java")) {
                continue;
            }
            String marker = stabilityMarker(Files.readString(sourceUnit.path()));
            if (marker != null) {
                markers.put(packageName(sourceUnit.compilationUnit()), marker);
            }
        }
        return markers;
    }

    private static String stabilityMarker(String source) {
        for (String marker : List.of("PublicApi", "Spi", "Internal", "Experimental")) {
            if (source.contains("@io.github.gear4jtest.core.api.annotation." + marker)) {
                return marker;
            }
        }
        return null;
    }

    private static InternalCatalog internalCatalog(List<SourceUnit> sourceUnits,
                                                   Map<String, String> packageMarkers) {
        Set<String> internalPackages = new HashSet<>();
        packageMarkers.forEach((packageName, marker) -> {
            if ("Internal".equals(marker)) {
                internalPackages.add(packageName);
            }
        });

        Set<String> internalTypes = new HashSet<>();
        for (SourceUnit sourceUnit : sourceUnits) {
            String packageName = packageName(sourceUnit.compilationUnit());
            boolean internalPackage = internalPackages.contains(packageName);
            for (Tree typeDeclaration : sourceUnit.compilationUnit().getTypeDecls()) {
                if (typeDeclaration instanceof ClassTree classTree) {
                    collectInternalTypes(classTree, packageName, internalPackage, internalTypes);
                }
            }
        }
        return new InternalCatalog(Set.copyOf(internalPackages), Set.copyOf(internalTypes));
    }

    private static void collectInternalTypes(ClassTree classTree,
                                             String enclosingName,
                                             boolean enclosingInternal,
                                             Set<String> internalTypes) {
        String qualifiedName = enclosingName + "." + classTree.getSimpleName();
        boolean internal = enclosingInternal || hasInternalMarker(classTree.getModifiers());
        if (internal) {
            internalTypes.add(qualifiedName);
        }
        for (Tree member : classTree.getMembers()) {
            if (member instanceof ClassTree nestedType) {
                collectInternalTypes(nestedType, qualifiedName, internal, internalTypes);
            }
        }
    }

    private static boolean hasInternalMarker(ModifiersTree modifiers) {
        return modifiers.getAnnotations().stream()
                .map(annotation -> annotation.getAnnotationType().toString())
                .anyMatch(annotation -> annotation.equals("Internal") || annotation.endsWith(".Internal"));
    }

    private static String packageName(CompilationUnitTree compilationUnit) {
        return compilationUnit.getPackageName() == null ? "" : compilationUnit.getPackageName().toString();
    }

    private record SourceUnit(Path path, CompilationUnitTree compilationUnit) {}

    private record InternalCatalog(Set<String> packages, Set<String> types) {
        boolean contains(String qualifiedName) {
            return packages.stream().anyMatch(packageName -> qualifiedName.startsWith(packageName + "."))
                    || types.stream().anyMatch(typeName -> qualifiedName.equals(typeName)
                            || qualifiedName.startsWith(typeName + "."));
        }
    }

    private static final class SignatureInspector {
        private final Path repositoryRoot;
        private final SourceUnit sourceUnit;
        private final InternalCatalog internalCatalog;
        private final Set<String> violations;
        private final String packageName;
        private final Map<String, String> explicitImports = new HashMap<>();
        private final List<String> wildcardImports = new ArrayList<>();
        private final List<String> staticWildcardOwners = new ArrayList<>();

        private SignatureInspector(Path repositoryRoot,
                                   SourceUnit sourceUnit,
                                   InternalCatalog internalCatalog,
                                   Set<String> violations) {
            this.repositoryRoot = repositoryRoot;
            this.sourceUnit = sourceUnit;
            this.internalCatalog = internalCatalog;
            this.violations = violations;
            packageName = packageName(sourceUnit.compilationUnit());
            collectImports();
        }

        private void collectImports() {
            for (ImportTree importTree : sourceUnit.compilationUnit().getImports()) {
                String importedName = importTree.getQualifiedIdentifier().toString();
                if (importedName.endsWith(".*")) {
                    String owner = importedName.substring(0, importedName.length() - 2);
                    if (importTree.isStatic()) {
                        staticWildcardOwners.add(owner);
                    } else {
                        wildcardImports.add(owner);
                    }
                    continue;
                }
                int separator = importedName.lastIndexOf('.');
                explicitImports.put(importedName.substring(separator + 1), importedName);
            }
        }

        private void inspectTopLevelType(ClassTree classTree) {
            if (!classTree.getModifiers().getFlags().contains(Modifier.PUBLIC)
                    || hasInternalMarker(classTree.getModifiers())) {
                return;
            }
            inspectType(classTree, classTree.getSimpleName().toString());
        }

        private void inspectType(ClassTree classTree, String ownerName) {
            if (hasInternalMarker(classTree.getModifiers())) {
                return;
            }

            inspectAnnotations(classTree.getModifiers().getAnnotations(), ownerName);
            inspectTypeParameters(classTree.getTypeParameters(), ownerName);
            inspectSignature(classTree.getExtendsClause(), ownerName);
            classTree.getImplementsClause().forEach(type -> inspectSignature(type, ownerName));
            classTree.getPermitsClause().forEach(type -> inspectSignature(type, ownerName));

            boolean interfaceLike = classTree.getKind() == Tree.Kind.INTERFACE
                    || classTree.getKind() == Tree.Kind.ANNOTATION_TYPE;
            boolean record = classTree.getKind() == Tree.Kind.RECORD;
            for (Tree member : classTree.getMembers()) {
                if (member instanceof ClassTree nestedType) {
                    if (isExported(nestedType.getModifiers(), interfaceLike)) {
                        inspectType(nestedType,
                                    ownerName + "." + nestedType.getSimpleName());
                    }
                } else if (member instanceof MethodTree methodTree) {
                    if (isExported(methodTree.getModifiers(), interfaceLike)
                            && !hasInternalMarker(methodTree.getModifiers())) {
                        inspectMethod(methodTree, ownerName);
                    }
                } else if (member instanceof VariableTree variableTree) {
                    boolean recordComponent = record
                            && !variableTree.getModifiers().getFlags().contains(Modifier.STATIC);
                    if ((recordComponent || isExported(variableTree.getModifiers(), interfaceLike))
                            && !hasInternalMarker(variableTree.getModifiers())) {
                        inspectVariable(variableTree, ownerName);
                    }
                }
            }
        }

        private static boolean isExported(ModifiersTree modifiers, boolean implicitlyPublic) {
            Set<Modifier> flags = modifiers.getFlags();
            if (flags.contains(Modifier.PRIVATE)) {
                return false;
            }
            return implicitlyPublic
                    || flags.contains(Modifier.PUBLIC)
                    || flags.contains(Modifier.PROTECTED);
        }

        private void inspectMethod(MethodTree methodTree, String ownerName) {
            String methodName = methodTree.getName().contentEquals("<init>")
                    ? ownerName
                    : ownerName + "#" + methodTree.getName() + "()";
            inspectAnnotations(methodTree.getModifiers().getAnnotations(), methodName);
            inspectTypeParameters(methodTree.getTypeParameters(), methodName);
            inspectSignature(methodTree.getReturnType(), methodName);
            if (methodTree.getReceiverParameter() != null) {
                inspectVariable(methodTree.getReceiverParameter(), methodName);
            }
            methodTree.getParameters().forEach(parameter -> inspectVariable(parameter, methodName));
            methodTree.getThrows().forEach(type -> inspectSignature(type, methodName));
        }

        private void inspectVariable(VariableTree variableTree, String ownerName) {
            inspectAnnotations(variableTree.getModifiers().getAnnotations(), ownerName);
            inspectSignature(variableTree.getType(), ownerName);
        }

        private void inspectTypeParameters(List<? extends TypeParameterTree> typeParameters, String ownerName) {
            for (TypeParameterTree typeParameter : typeParameters) {
                inspectAnnotations(typeParameter.getAnnotations(), ownerName);
                typeParameter.getBounds().forEach(type -> inspectSignature(type, ownerName));
            }
        }

        private void inspectAnnotations(List<? extends AnnotationTree> annotations, String ownerName) {
            annotations.forEach(annotation -> inspectSignature(annotation.getAnnotationType(), ownerName));
        }

        private void inspectSignature(Tree typeTree, String ownerName) {
            if (typeTree == null) {
                return;
            }
            Matcher matcher = JAVA_TYPE_TOKEN.matcher(typeTree.toString());
            while (matcher.find()) {
                resolveInternalReference(matcher.group()).forEach(internalType -> violations.add(
                                                                                                 repositoryRoot
                                                                                                         .relativize(
                                                                                                                     sourceUnit
                                                                                                                             .path())
                                                                                                         + ": "
                                                                                                         + ownerName
                                                                                                         + " -> "
                                                                                                         + internalType));
            }
        }

        private Set<String> resolveInternalReference(String token) {
            Set<String> resolved = new HashSet<>();
            addIfInternal(resolved, token);

            int separator = token.indexOf('.');
            String rootName = separator < 0 ? token : token.substring(0, separator);
            String suffix = separator < 0 ? "" : token.substring(separator);

            String explicitImport = explicitImports.get(rootName);
            if (explicitImport != null) {
                addIfInternal(resolved, explicitImport + suffix);
            }
            addIfInternal(resolved, packageName + "." + token);
            wildcardImports.forEach(importedPackage -> addIfInternal(
                                                                     resolved,
                                                                     importedPackage + "." + token));
            staticWildcardOwners.forEach(owner -> addIfInternal(resolved, owner + "." + token));
            return resolved;
        }

        private void addIfInternal(Set<String> resolved, String candidate) {
            if (internalCatalog.contains(candidate)) {
                resolved.add(candidate);
            }
        }
    }
}
