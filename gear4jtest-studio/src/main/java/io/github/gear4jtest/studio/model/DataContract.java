package io.github.gear4jtest.studio.model;

/** Java type names alone cannot prove an assignability mismatch. */
public record DataContract(String javaType, String schemaId) {
    public enum Compatibility {
        COMPATIBLE, INCOMPATIBLE, UNKNOWN
    }

    public Compatibility compatibilityWith(DataContract next) {
        return javaType != null && javaType.equals(next.javaType) && schemaId == null && next.schemaId == null
                ? Compatibility.COMPATIBLE : Compatibility.UNKNOWN;
    }

    public static DataContract text() {
        return new DataContract("java.lang.String", null);
    }
}
