// package io.github.gear4jtest.core.engine.spi;
//
// import io.github.gear4jtest.core.engine.DryRunConfiguration;
// import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
// import io.github.gear4jtest.core.api.station.AbstractStation;
// import io.github.gear4jtest.core.api.context.ExecutionContext;
// import io.github.gear4jtest.core.model.Station;
// import io.github.gear4jtest.core.api.context.StationExecutionContext;
// import io.github.gear4jtest.core.execution.trace.StationLogTrace;
//
/// **
// * Orchestrateur pour le mode Simulation (Dry Run).
// * Utilise DryRunConfiguration pour décider s'il faut mocker, bloquer ou
// exécuter.
// */
// public class DryRunStationRunner implements StationRunner {
// private final StrategyRegistry registry;
// private final DryRunConfiguration config;
// private final MockLogFactory mockFactory;
//
// public DryRunStationRunner(StrategyRegistry registry, DryRunConfiguration
// config) {
// this.registry = registry;
// this.config = config;
// this.mockFactory = new MockLogFactory();
// }
//
// @Override
// public StationLogTrace run(Object input, AbstractStation station,
// StationExecutionContext ctx) {
// String id = station.getId();
//
// // 1. MOCK (Interception)
// if (config.hasMockData(id)) {
// return mockFactory.createMock(
// id,
// ctx.getPipelineExecutionId(),
// ctx.getCurrentParentLogId(),
// config.getMockData(id)
// );
// }
//
// // 2. SAFETY CHECK
// if (config.isUnsafe(id)) {
// throw new IllegalStateException("Unsafe station detected: " + id);
// }
//
// // 3. EXÉCUTION RÉELLE (Mais pilotée par NOUS)
//
// // A. Chargement des paramètres (Standard + Overrides du DryRun)
// // On fusionne les params de base avec les overrides de test
// ctx.loadStationParameters(station.getParameters());
// if (config.hasParamOverrides(id)) {
// ctx.applyParamOverrides(config.getParamOverrides(id));
// }
//
// // B. Récupération de la stratégie
// var strategy = registry.getStrategy(station);
//
// // C. APPEL CRITIQUE : On passe 'this' !
// // C'est ce qui garantit que les enfants passeront à nouveau par cette
// méthode run()
// return strategy.execute(station, input, ctx, this);
// }
// }
