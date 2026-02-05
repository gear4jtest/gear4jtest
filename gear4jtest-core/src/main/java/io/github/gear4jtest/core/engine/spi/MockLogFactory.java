package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.persistence.StationLog;
import java.util.HashMap;
import java.util.Map;

/**
 * Responsabilité : Fabriquer des instances de StationLog configurées pour représenter
 * une exécution simulée (Mock).
 *
 * Pattern : Pure Fabrication / Factory.
 * Cette classe isole la complexité d'initialisation et d'enrichissement des métadonnées
 * pour ne pas polluer le Runner.
 */
class MockLogFactory {

    private static final String META_EXECUTION_MODE = "execution.mode";
    private static final String MODE_MOCK_DATA = "MOCKED_DATA";

    /**
     * Crée un StationLog en succès immédiat avec les données mockées.
     */
    StationLog createMock(String stationId, String pipelineExecutionId, String parentOperationId, Object mockData) {
        // 1. Utilisation de l'API standard du modèle (Lifecycle start)
        StationLog log = StationLog.start(pipelineExecutionId, stationId, parentOperationId);

        // 2. Simulation du succès
        log.markSuccess(mockData);

        // 3. Ajustement temporel : Un mock est instantané (start == end)
        // Cela permet de distinguer visuellement un mock d'une exécution réelle très rapide (1ms)
        log.setEndedAt(log.getStartedAt());

        // 4. Enrichissement des métadonnées (Sans modifier la classe StationLog)
        injectMockMetadata(log);

        return log;
    }

    private void injectMockMetadata(StationLog log) {
        // On copie la map existante ou on en crée une nouvelle pour éviter les NullPointer
        Map<String, Object> context = log.getContext();
        Map<String, Object> newContext = (context != null) ? new HashMap<>(context) : new HashMap<>();

        // Ajout du flag indiquant que c'est du beurre
        newContext.put(META_EXECUTION_MODE, MODE_MOCK_DATA);

        log.setContext(newContext);
    }
}