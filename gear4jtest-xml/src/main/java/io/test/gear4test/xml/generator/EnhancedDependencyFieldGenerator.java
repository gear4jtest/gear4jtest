package io.test.gear4test.xml.generator;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import io.test.gear4jtest.xml.generated.DependencyType;

public class EnhancedDependencyFieldGenerator {
    
    public List<FieldSpec> generateFields(List<DependencyType> dependencies) {
        return dependencies.stream()
            .map(this::createField)
            .collect(Collectors.toList());
    }
    
    public List<ClassName> getRequiredImports(Set<ResolvedDependency> dependencies) {
        return dependencies.stream()
            .filter(dep -> !dep.getFullyQualifiedName().equals("Object"))
            .map(dep -> ClassName.bestGuess(dep.getFullyQualifiedName()))
            .collect(Collectors.toList());
    }
    
    public MethodSpec generateConstructor(List<DependencyType> dependencies) {
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addJavadoc("Constructeur avec injection de dépendances\n");
        
        // Ajouter les paramètres
        dependencies.forEach(dep -> {
            constructorBuilder.addParameter(safeGetType(dep), dep.getName());
            
//            String javadocLine = dep.isExplicitlyMapped()
//                ? String.format("@param %s %s", dep.getName(), dep.getDescription())
//                : String.format("@param %s %s (type inféré)", dep.getName(), dep.getDescription());
//            constructorBuilder.addJavadoc(javadocLine + "\n");
        });
        
        // Ajouter les assignations
        CodeBlock.Builder code = CodeBlock.builder();
        dependencies.forEach(dep -> {
            code.add("this.$L = $L;\n", dep.getName(), dep.getName());
        });
        
        return constructorBuilder.addCode(code.build()).build();
    }
    
    public MethodSpec generateValidationReport(Set<ResolvedDependency> dependencies) {
        Set<ResolvedDependency> unmappedDeps = dependencies.stream()
            .filter(dep -> !dep.isExplicitlyMapped())
            .collect(Collectors.toSet());
        
        if (unmappedDeps.isEmpty()) {
            return null; // Pas de rapport nécessaire
        }
        
        CodeBlock.Builder code = CodeBlock.builder()
            .add("// AVERTISSEMENT: Dépendances avec types inférés automatiquement:\n");
        
        unmappedDeps.forEach(dep -> {
            code.add("// - $L -> $L ($L)\n", 
                dep.getName(), 
                dep.getFullyQualifiedName());
        });
        
        return MethodSpec.methodBuilder("getDependencyValidationReport")
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .returns(void.class)
            .addJavadoc("Rapport de validation des dépendances")
            .addCode(code.build())
            .build();
    }
    
    private FieldSpec createField(DependencyType dependency) {
        return FieldSpec.builder(
                        safeGetType(dependency),
                        dependency.getName()
                )
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();
    }

    private static Type safeGetType(DependencyType dependency) {
        try {
            return Class.forName(dependency.getType());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Type not found for dependency: " + dependency.getName(), e);
        }
    }
}
