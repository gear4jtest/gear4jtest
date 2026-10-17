package io.github.gear4jtest.studio.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Host metadata; UNKNOWN is a lack of proof, never an execution authorization.
 */
public record DataContract(String javaType,
                           String schemaId,
                           Origin origin,
                           boolean complete,
                           Set<String> assignableTo,
                           Shape shape) {
    public enum Compatibility {
        COMPATIBLE, INCOMPATIBLE, UNKNOWN
    }

    public enum Origin {
        DECLARED, INFERRED, UNKNOWN
    }

    /**
     * A closed shape guarantees no other fields; required output fields are
     * guaranteed present.
     */
    public record Shape(Map<String, Field> fields, boolean closed) {
        public Shape {
            fields = java.util.Collections.unmodifiableMap(new TreeMap<>(fields));
        }
    }

    public record Field(DataContract contract, boolean required) {
        public Field {
            Objects.requireNonNull(contract);
        }
    }

    public DataContract {
        Objects.requireNonNull(origin);
        assignableTo = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(assignableTo));
    }

    public DataContract(String javaType, String schemaId) {
        this(javaType, schemaId, Origin.DECLARED, javaType != null, Set.of(), null);
    }

    public static DataContract unknown() {
        return new DataContract(null, null, Origin.UNKNOWN, false, Set.of(), null);
    }

    public static DataContract text() {
        return new DataContract("java.lang.String", null);
    }

    public Compatibility compatibilityWith(DataContract next) {
        Compatibility java = javaCompatibility(next);
        Compatibility structure = shapeCompatibility(next);
        if (java == Compatibility.INCOMPATIBLE || structure == Compatibility.INCOMPATIBLE)
            return Compatibility.INCOMPATIBLE;
        if (java == Compatibility.UNKNOWN || structure == Compatibility.UNKNOWN)
            return Compatibility.UNKNOWN;
        return Compatibility.COMPATIBLE;
    }

    private Compatibility javaCompatibility(DataContract next) {
        if (!complete || !next.complete || javaType == null || next.javaType == null)
            return Compatibility.UNKNOWN;
        if (javaType.equals(next.javaType) || assignableTo.contains(next.javaType))
            return Compatibility.COMPATIBLE;
        if (javaType.contains("<") && next.javaType.contains("<")
                && javaType.substring(0, javaType.indexOf('<'))
                        .equals(next.javaType.substring(0, next.javaType.indexOf('<'))))
            return Compatibility.INCOMPATIBLE;
        // A populated closure is an exhaustive Java assignability proof for non-generic
        // types only.
        if (!assignableTo.isEmpty() && !javaType.contains("<") && !next.javaType.contains("<"))
            return Compatibility.INCOMPATIBLE;
        return Compatibility.UNKNOWN;
    }

    private Compatibility shapeCompatibility(DataContract next) {
        if (next.schemaId != null && !next.schemaId.equals(schemaId))
            return Compatibility.UNKNOWN;
        if (next.shape == null)
            return Compatibility.COMPATIBLE;
        if (shape == null)
            return Compatibility.UNKNOWN;
        var result = Compatibility.COMPATIBLE;
        for (var entry : next.shape.fields.entrySet()) {
            var expected = entry.getValue();
            var actual = shape.fields.get(entry.getKey());
            if (actual == null) {
                if (expected.required && shape.closed)
                    return Compatibility.INCOMPATIBLE;
                if (!shape.closed)
                    result = Compatibility.UNKNOWN;
            } else {
                var compatibility = actual.contract.compatibilityWith(expected.contract);
                if (compatibility == Compatibility.INCOMPATIBLE)
                    return compatibility;
                if (compatibility == Compatibility.UNKNOWN || expected.required && !actual.required)
                    result = Compatibility.UNKNOWN;
            }
        }
        if (next.shape.closed) {
            if (!shape.closed)
                result = Compatibility.UNKNOWN;
            for (var entry : shape.fields.entrySet()) {
                if (!next.shape.fields.containsKey(entry.getKey())) {
                    if (entry.getValue().required)
                        return Compatibility.INCOMPATIBLE;
                    result = Compatibility.UNKNOWN;
                }
            }
        }
        return result;
    }
}
