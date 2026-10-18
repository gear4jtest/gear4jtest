package io.github.gear4jtest.studio.standalone;

/** Configured grouping of management targets; not a runtime deployment. */
public record StudioApplication(String id, String name) {
    public StudioApplication {
        TargetDirectory.identifier(id);
        TargetDirectory.label(name);
    }
}
