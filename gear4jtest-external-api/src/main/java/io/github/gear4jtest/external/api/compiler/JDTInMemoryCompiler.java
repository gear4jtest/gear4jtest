package io.github.gear4jtest.external.api.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.external.api.exception.CompilationException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.ModuleBinding;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

public class JDTInMemoryCompiler implements GeneratedSourceCompiler {
    private final ClassLoader parentClassLoader;
    private final InMemoryNameEnvironment nameEnvironment;

    public JDTInMemoryCompiler() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public JDTInMemoryCompiler(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader != null ? parentClassLoader : ClassLoader.getSystemClassLoader();
        this.nameEnvironment = new InMemoryNameEnvironment(this.parentClassLoader);
    }

    private static CompilerOptions compilerOptions() {
        CompilerOptions options = new CompilerOptions();
        options.set(compilerOptionValues());
        return options;
    }

    static Map<String, String> compilerOptionValues() {
        Map<String, String> optionsMap = new HashMap<>();
        optionsMap.put(CompilerOptions.OPTION_Source, CompilerOptions.VERSION_17);
        optionsMap.put(CompilerOptions.OPTION_TargetPlatform, CompilerOptions.VERSION_17);
        optionsMap.put(CompilerOptions.OPTION_Compliance, CompilerOptions.VERSION_17);
        optionsMap.put(CompilerOptions.OPTION_Encoding, StandardCharsets.UTF_8.name());
        optionsMap.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.IGNORE);
        optionsMap.put(CompilerOptions.OPTION_ReportUnusedImport, CompilerOptions.IGNORE);
        optionsMap.put(CompilerOptions.OPTION_EnablePreviews, CompilerOptions.DISABLED);
        optionsMap.put(CompilerOptions.OPTION_Release, CompilerOptions.ENABLED);
        optionsMap.put(CompilerOptions.OPTION_ReportUnstableAutoModuleName, CompilerOptions.DISABLED);
        optionsMap.put(CompilerOptions.OPTION_IgnoreUnnamedModuleForSplitPackage, CompilerOptions.DISABLED);
        return Map.copyOf(optionsMap);
    }

    /**
     * Compiles a single Java source unit in memory.
     *
     * @param className  fully-qualified class name
     * @param sourceCode UTF-8 Java source bytes
     * @return compiled class bytes by fully-qualified class name
     */
    @Override
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        try {
            InMemoryCompilationUnit unit = new InMemoryCompilationUnit(className, sourceCode);
            InMemoryCompilerRequestor requestor = new InMemoryCompilerRequestor();

            Compiler compiler = new Compiler(nameEnvironment, DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                    compilerOptions(), requestor, new DefaultProblemFactory());

            compiler.compile(new ICompilationUnit[] { unit });

            if (requestor.hasErrors()) {
                throw new CompilationException("Compilation failed for " + className, requestor.getErrors());
            }
            if (requestor.getCompiledClasses().isEmpty()) {
                throw new CompilationException("Compilation produced no class for " + className);
            }
            return requestor.getCompiledClasses();
        } catch (CompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new CompilationException("JDT compilation failed for " + className, List.of(e.getMessage()), e);
        }
    }

    public ClassLoader parentClassLoader() {
        return parentClassLoader;
    }

    private static class InMemoryCompilationUnit implements ICompilationUnit {
        private final String className;
        private final byte[] sourceCode;
        private final String fileName;

        private InMemoryCompilationUnit(String className, byte[] sourceCode) {
            this.className = className;
            this.sourceCode = sourceCode;
            this.fileName = className.replace('.', '/') + ".java";
        }

        @Override
        public char[] getContents() {
            return new String(sourceCode, StandardCharsets.UTF_8).toCharArray();
        }

        @Override
        public char[] getMainTypeName() {
            int lastDot = className.lastIndexOf('.');
            return (lastDot == -1 ? className : className.substring(lastDot + 1)).toCharArray();
        }

        @Override
        public char[][] getPackageName() {
            int lastDot = className.lastIndexOf('.');
            if (lastDot == -1) {
                return new char[0][];
            }
            String[] parts = className.substring(0, lastDot).split("\\.");
            char[][] result = new char[parts.length][];
            for (int i = 0; i < parts.length; i++) {
                result[i] = parts[i].toCharArray();
            }
            return result;
        }

        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }

        @Override
        public ModuleBinding module(LookupEnvironment environment) {
            return environment.getModule(ModuleBinding.UNNAMED);
        }

        @Override
        public char[] getFileName() {
            return fileName.toCharArray();
        }
    }

    private static class InMemoryNameEnvironment implements INameEnvironment {
        private final ClassLoader classLoader;

        private InMemoryNameEnvironment(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        private static String join(char[][] parts) {
            if (parts == null || parts.length == 0) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    result.append('.');
                }
                result.append(parts[i]);
            }
            return result.toString();
        }

        @Override
        public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
            return findType(join(compoundTypeName));
        }

        @Override
        public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
            String pkg = join(packageName);
            String simple = new String(typeName);
            return findType(pkg.isBlank() ? simple : pkg + "." + simple);
        }

        private NameEnvironmentAnswer findType(String className) {
            try {
                String resourceName = className.replace('.', '/') + ".class";
                try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
                    if (is == null) {
                        return null;
                    }
                    byte[] classBytes = is.readAllBytes();
                    ClassFileReader classFileReader = new ClassFileReader(classBytes, className.toCharArray(), true);
                    return new NameEnvironmentAnswer(classFileReader, null);
                }
            } catch (IOException | ClassFormatException e) {
                return null;
            }
        }

        @Override
        public boolean isPackage(char[][] parentPackageName, char[] packageName) {
            String parent = join(parentPackageName);
            String candidate = parent.isBlank() ? new String(packageName) : parent + "." + new String(packageName);

            if (packageName.length > 0 && Character.isUpperCase(packageName[0])) {
                return false;
            }

            String resourceName = candidate.replace('.', '/') + ".class";
            return classLoader.getResource(resourceName) == null;
        }

        @Override
        public void cleanup() {
            // nothing to clean
        }
    }

    private static class InMemoryCompilerRequestor implements ICompilerRequestor {
        private final Map<String, byte[]> compiledClasses = new HashMap<>();
        private final List<String> errors = new ArrayList<>();

        private static String format(IProblem problem) {
            return "%s:%d:%d: %s".formatted(new String(problem.getOriginatingFileName()), problem.getSourceLineNumber(),
                                            problem.getSourceStart(), problem.getMessage());
        }

        @Override
        public void acceptResult(CompilationResult result) {
            if (result.hasErrors()) {
                for (IProblem problem : result.getAllProblems()) {
                    if (problem.isError()) {
                        errors.add(format(problem));
                    }
                }
                return;
            }

            for (var classFile : result.getClassFiles()) {
                char[][] compoundName = classFile.getCompoundName();
                String className = String.join(".",
                                               Arrays.stream(compoundName).map(String::new).toArray(String[]::new));
                compiledClasses.put(className, classFile.getBytes());
            }
        }

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        List<String> getErrors() {
            return errors;
        }

        Map<String, byte[]> getCompiledClasses() {
            return compiledClasses;
        }
    }
}
