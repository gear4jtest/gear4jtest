package io.test.gear4test.xml.visitor;

import io.test.gear4jtest.xml.generated.BaseOperationType;
import java.util.HashMap;
import java.util.Map;

/**
 * Générateur de noms de méthodes pour les opérations.
 * Compatible Java 17.
 */
public class MethodNameGenerator {
    
    private final Map<String, String> methodNames = new HashMap<>();
    
    public String generate(BaseOperationType operation) {
        String id = operation.getId();
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Opération sans ID: " + operation.getClass().getSimpleName());
        }
        
        return methodNames.computeIfAbsent(id, this::createMethodName);
    }
    
    private String createMethodName(String id) {
        return "create" + capitalize(id);
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}