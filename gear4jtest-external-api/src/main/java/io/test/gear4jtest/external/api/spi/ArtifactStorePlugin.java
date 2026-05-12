package io.test.gear4jtest.external.api.spi;

import java.util.Map;

import io.test.gear4jtest.external.api.artifact.ArtifactStore;

public interface ArtifactStorePlugin {
    /**
     * Nom canonique du type, ex: "S3", "SFTP", "DATABASE", "FILESYSTEM", "MEMORY"
     */
    String type();

    /**
     * Construit un store à partir de props. Aucun type tiers dans la signature.
     */
    ArtifactStore build(Map<String, String> props, Context ctx) throws Exception;

    /**
     * Contexte générique pour passer des ressources optionnelles (ex: DataSource,
     * logger…).
     */
    interface Context {
        /**
         * Lookup générique par clé, pour éviter toute dépendance forte dans l’API.
         */
        Object lookup(String key);

        default void warn(String msg) {
        }

        default void info(String msg) {
        }
    }
}
