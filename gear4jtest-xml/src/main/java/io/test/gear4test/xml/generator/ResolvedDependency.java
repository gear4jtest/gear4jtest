package io.test.gear4test.xml.generator;

import java.util.Objects;

public class ResolvedDependency {
    private final String name;
    private final String fullyQualifiedName;
    private final String simpleClassName;
    private final boolean explicitlyMapped;

    public ResolvedDependency(String name,
                              String fullyQualifiedName,
                              String simpleClassName,
                              boolean explicitlyMapped) {
        this.name = name;
        this.fullyQualifiedName = fullyQualifiedName;
        this.simpleClassName = simpleClassName;
        this.explicitlyMapped = explicitlyMapped;
    }

    // Getters
    public String getName() {
        return name;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public String getSimpleClassName() {
        return simpleClassName;
    }

    public boolean isExplicitlyMapped() {
        return explicitlyMapped;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ResolvedDependency that = (ResolvedDependency) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
