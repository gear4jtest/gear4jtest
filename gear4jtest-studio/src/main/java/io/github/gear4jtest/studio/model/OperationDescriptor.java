package io.github.gear4jtest.studio.model;

import java.util.List;
import java.util.Map;

public record OperationDescriptor(String id,
                                  String name,
                                  String description,
                                  String effects,
                                  DataContract input,
                                  DataContract output,
                                  boolean testAllowed,
                                  Map<String, Parameter> parameters) {
    public OperationDescriptor {
        parameters = Map.copyOf(parameters);
    }

    public record Parameter(String label, ParameterValue.Kind kind, boolean required, List<String> allowedReferences) {
        public Parameter {
            allowedReferences = List.copyOf(allowedReferences);
        }
    }
}
