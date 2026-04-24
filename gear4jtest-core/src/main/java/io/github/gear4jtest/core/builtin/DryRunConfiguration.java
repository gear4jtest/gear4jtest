//package io.github.gear4jtest.core.engine;
//
//import java.util.*;
//
///**
// * Conteneur de configuration pour une exécution en mode Simulation (Dry Run).
// * Cet objet est généralement construit par le DryRunConfigParser à partir d'un JSON/YAML.
// */
//public class DryRunConfiguration {
//
//    // 1. DATA MOCKING : Remplacement complet du résultat d'une station.
//    // La clé est l'ID de la station.
//    private final Map<String, Object> stationMockResults = new HashMap<>();
//
//    // 2. DEPENDENCY MOCKING : Liste des classes (Opérateurs/Services) à remplacer par des Proxies.
//    // Utilisé par la ShadowResourceFactory.
//    private final Set<Class<?>> dependenciesToMock = new HashSet<>();
//
//    // 3. PARAMETER OVERRIDES : Surcharge des paramètres de configuration.
//    // Map<StationID, Map<ParamKey, Value>>
//    private final Map<String, Map<String, Object>> stationParamOverrides = new HashMap<>();
//
//    // 4. SAFETY : Liste des stations considérées comme dangereuses (ex: écriture DB)
//    // Si une station est ici ET n'a pas de mock de résultat, le DryRun doit échouer.
//    private final Set<String> unsafeStationIds = new HashSet<>();
//
//
//    // --- GESTION DES RÉSULTATS MOCKÉS (Pour DryRunStationRunner) ---
//
//    public void addMockResult(String stationId, Object result) {
//        this.stationMockResults.put(stationId, result);
//        // Si on mock le résultat, on considère implicitement que l'exécution réelle est évitée,
//        // donc on peut marquer l'ID comme "traité" ou unsafe safe-guardé.
//    }
//
//    public boolean hasMockResult(String stationId) {
//        return stationMockResults.containsKey(stationId);
//    }
//
//    public Object getMockResult(String stationId) {
//        return stationMockResults.get(stationId);
//    }
//
//
//    // --- GESTION DES DÉPENDANCES (Pour ShadowResourceFactory) ---
//
//    public void addDependencyToMock(Class<?> clazz) {
//        this.dependenciesToMock.add(clazz);
//    }
//
//    public boolean shouldMockDependency(Class<?> clazz) {
//        return dependenciesToMock.contains(clazz);
//    }
//
//
//    // --- GESTION DES PARAMÈTRES (Pour WorkStationStrategy / Context) ---
//
//    public void addParamOverride(String stationId, String key, Object value) {
//        stationParamOverrides
//            .computeIfAbsent(stationId, k -> new HashMap<>())
//            .put(key, value);
//    }
//
//    /**
//     * Retourne les surcharges pour une station donnée.
//     * Ne retourne jamais null (Map vide si pas de surcharge).
//     */
//    public Map<String, Object> getParamOverrides(String stationId) {
//        return stationParamOverrides.getOrDefault(stationId, Collections.emptyMap());
//    }
//
//
//    // --- GESTION DE LA SÉCURITÉ ---
//
//    public void markUnsafe(String stationId) {
//        this.unsafeStationIds.add(stationId);
//    }
//
//    public boolean isUnsafe(String stationId) {
//        return unsafeStationIds.contains(stationId);
//    }
//}
