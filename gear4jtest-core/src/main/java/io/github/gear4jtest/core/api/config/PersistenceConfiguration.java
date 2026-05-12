package io.github.gear4jtest.core.api.config;

public class PersistenceConfiguration {

    // On garde ça : Est-ce qu'on veut stocker le JSON du résultat final ?
    // C'est un choix métier (coût stockage vs auditabilité).
    private final boolean storeResultObject;

    // On pourrait ajouter d'autres options de "tuning" ici :
    // private final boolean storeIntermediateSteps; (Garder le détail des
    // opérations ou juste le résumé ?)
    // private final int flushThreshold; (Pour la performance spécifique de ce
    // pipeline)

    private PersistenceConfiguration(boolean storeResultObject) {
        this.storeResultObject = storeResultObject;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isStoreResultObject() {
        return storeResultObject;
    }

    public static class Builder {
        private boolean storeResultObject = true; // Valeur par défaut

        public Builder storeResultObject(boolean storeResultObject) {
            this.storeResultObject = storeResultObject;
            return this;
        }

        public PersistenceConfiguration build() {
            return new PersistenceConfiguration(storeResultObject);
        }
    }
}
