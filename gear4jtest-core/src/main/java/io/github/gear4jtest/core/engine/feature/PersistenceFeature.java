package io.github.gear4jtest.core.engine.feature;

import io.github.gear4jtest.core.engine.api.PipelineFeature;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import java.util.Objects;

public class PersistenceFeature implements PipelineFeature {

    public static final String KEY = "PERSISTENCE";

    private final AssemblyRunManager manager;

    /**
     * Active la persistance.
     * @param manager Le gestionnaire de persistance (DB, Memory, File...) OBLIGATOIRE.
     * C'est lui qui sait comment stocker les logs.
     */
    public PersistenceFeature(AssemblyRunManager manager) {
        this.manager = Objects.requireNonNull(manager, "Impossible d'activer la persistance sans fournir un AssemblyRunManager.");
    }

    public AssemblyRunManager getManager() {
        return manager;
    }

    @Override
    public String getFeatureKey() {
        return KEY;
    }
}
