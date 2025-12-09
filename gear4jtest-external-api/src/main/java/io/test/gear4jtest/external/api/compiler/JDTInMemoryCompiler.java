package io.test.gear4jtest.external.api.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.test.gear4jtest.external.api.exception.CompilationException;
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

public class JDTInMemoryCompiler {

    private final InMemoryNameEnvironment nameEnvironment;

    public JDTInMemoryCompiler() {
        this.nameEnvironment = new InMemoryNameEnvironment();
    }

    /**
     * Compile avec JDT en mémoire
     */
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        try {
            // Création de l'unité de compilation
            InMemoryCompilationUnit unit = new InMemoryCompilationUnit(className, sourceCode);

            // Configuration du compilateur JDT
            CompilerOptions options = getCompilerOptions();

            // Requestor pour récupérer les résultats
            InMemoryCompilerRequestor requestor = new InMemoryCompilerRequestor();

            // Création du compilateur JDT
            Compiler compiler = new Compiler(
                    nameEnvironment,
                    DefaultErrorHandlingPolicies.proceedWithAllProblems(),
                    options,
                    requestor,
                    new DefaultProblemFactory()
            );

            // Compilation
            compiler.compile(new ICompilationUnit[]{unit});

            // Récupération des résultats
            if (requestor.hasErrors()) {
                System.err.println("Erreurs de compilation:");
                requestor.getErrors().forEach(System.err::println);
                return Map.of();
            }

            // Retourne les classes compilées
            return requestor.getCompiledClasses();
        } catch (Exception e) {
            System.err.println("Erreur JDT: " + e.getMessage());
            throw new CompilationException(e);
        }
    }

    /**
     * Configure les options du compilateur selon la version Java disponible
     */
    private CompilerOptions getCompilerOptions() {
        CompilerOptions options = new CompilerOptions();

        // Détection de la version Java
        String jdtVersion = CompilerOptions.VERSION_17;

        // Configuration des options
        Map<String, String> optionsMap = new HashMap<>();
        optionsMap.put(CompilerOptions.OPTION_Source, jdtVersion);
        optionsMap.put(CompilerOptions.OPTION_TargetPlatform, jdtVersion);
        optionsMap.put(CompilerOptions.OPTION_Compliance, jdtVersion);
        optionsMap.put(CompilerOptions.OPTION_Encoding, "UTF-8");
        optionsMap.put(CompilerOptions.OPTION_ReportDeprecation, CompilerOptions.IGNORE);
        optionsMap.put(CompilerOptions.OPTION_ReportUnusedImport, CompilerOptions.IGNORE);

        // Désactiver le système de modules pour éviter les erreurs
        optionsMap.put(CompilerOptions.OPTION_EnablePreviews, CompilerOptions.DISABLED);
        optionsMap.put(CompilerOptions.OPTION_Release, CompilerOptions.DISABLED);

        // Forcer le mode non-module (classpath traditionnel)
        optionsMap.put(CompilerOptions.OPTION_ReportUnstableAutoModuleName, CompilerOptions.DISABLED);
        optionsMap.put(CompilerOptions.OPTION_IgnoreUnnamedModuleForSplitPackage, CompilerOptions.DISABLED);

        options.set(optionsMap);

        return options;
    }

    /**
     * Unité de compilation en mémoire pour JDT
     */
    private static class InMemoryCompilationUnit implements ICompilationUnit {
        private final String className;
        private final byte[] sourceCode;
        private final String fileName;

        public InMemoryCompilationUnit(String className, byte[] sourceCode) {
            this.className = className;
            this.sourceCode = sourceCode;
            this.fileName = className.replace('.', '/') + ".java";
        }

        @Override
        public char[] getContents() {
            return new String(sourceCode).toCharArray();
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

            String packageName = className.substring(0, lastDot);
            String[] parts = packageName.split("\\.");
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

    /**
     * Environnement de noms pour JDT - version améliorée
     */
    private static class InMemoryNameEnvironment implements INameEnvironment {
        private final ClassLoader systemClassLoader;

        public InMemoryNameEnvironment() {
            this.systemClassLoader = ClassLoader.getSystemClassLoader();
        }

        @Override
        public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
            String result = "";

            String sep = "";

            for (int i = 0; i < compoundTypeName.length; i++) {
                result += sep;
                result += new String(compoundTypeName[i]);
                sep = ".";
            }

            return findType(result);
        }

        @Override
        public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
            String result = "";

            String sep = "";

            for (int i = 0; i < packageName.length; i++) {
                result += sep;
                result += new String(packageName[i]);
                sep = ".";
            }

            result += sep;
            result += new String(typeName);
            return findType(result);
        }


        private NameEnvironmentAnswer findType(String className) {
            try {
                String resourceName = className.replace('.', '/') + ".class";

                InputStream is = systemClassLoader.getResourceAsStream(resourceName);

                if (is == null) {
                    return null;
                }

                byte[] classBytes = is.readAllBytes();
                char[] fileName = className.toCharArray();
                ClassFileReader classFileReader = new ClassFileReader(classBytes, fileName, true);

                return new NameEnvironmentAnswer(classFileReader, null);
            } catch (IOException | ClassFormatException e) {
                return null;
            }
        }

        @Override
        public boolean isPackage(char[][] parentPackageName, char[] packageName) {
            String result = "";
            String sep = "";

            if (parentPackageName != null) {
                for (int i = 0; i < parentPackageName.length; i++) {
                    result += sep;
                    result += new String(parentPackageName[i]);
                    sep = ".";
                }
            }

            if (Character.isUpperCase(packageName[0])) {
                return false;
            }

            String str = new String(packageName);
            result += sep;
            result += str;
            return isPackage(result);
        }

        private boolean isPackage(String result) {
            String resourceName = "/" + result.replace('.', '/') + ".class";
            InputStream is = systemClassLoader.getResourceAsStream(resourceName);
            return is == null;
        }

        @Override
        public void cleanup() {
            // Rien à nettoyer
        }
    }

    /**
     * Requestor pour récupérer les résultats de compilation JDT
     */
    private static class InMemoryCompilerRequestor implements ICompilerRequestor {
        private final Map<String, byte[]> compiledClasses = new HashMap<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void acceptResult(CompilationResult result) {
            if (result.hasErrors()) {
                for (var problem : result.getAllProblems()) {
                    errors.add(problem.getMessage());
                }
            } else {
                // Récupérer les fichiers de classe générés
                var classFiles = result.getClassFiles();
                for (var classFile : classFiles) {
                    String className = new String(classFile.getCompoundName()[0]);
                    // Construire le nom complet de la classe
                    char[][] compoundName = classFile.getCompoundName();
                    if (compoundName.length > 1) {
                        className = String.join(".", Arrays.stream(compoundName)
                                .map(String::new)
                                .toArray(String[]::new));
                    }
                    compiledClasses.put(className, classFile.getBytes());
                }
            }
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public Map<String, byte[]> getCompiledClasses() {
            return compiledClasses;
        }
    }
}
