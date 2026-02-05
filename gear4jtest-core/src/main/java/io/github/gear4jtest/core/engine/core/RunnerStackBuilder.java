package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.api.PipelineFeature;
import io.github.gear4jtest.core.engine.spi.RuntimeExtension;
import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

public class RunnerStackBuilder {

    private final ExtensionRegistry registry;
    private final StrategyRegistry strategyRegistry;

    public RunnerStackBuilder(ExtensionRegistry registry, StrategyRegistry strategyRegistry) {
        this.registry = registry;
        this.strategyRegistry = strategyRegistry;
    }

    public StationRunner build(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
        // 1. Identification des extensions à activer
        List<RuntimeExtension> extensions = request.getExtensions();

        // On itère sur les features demandées dans la Request
//        for (PipelineFeature feature : request.getActiveFeatures()) {
//            registry.get(feature.getFeatureKey())
//                    .ifPresent(extensions::add);
//        }
        
        // TODO: Gérer ici le cas où la config vient de AssemblyLine.persistenceConfiguration
        // Si présent, et que la feature n'est PAS dans la request, on devrait peut-être
        // lever une erreur ou utiliser un défaut si on en a un.
        // Avec notre modèle "Explicite", c'est à l'appelant de traduire la config AssemblyLine en Feature.

        // 2. Phase PREPARE (Injection des ressources)
        for (RuntimeExtension ext : extensions) {
            ext.prepare(ctx, request);
        }

        // 3. Phase DECORATE (Empilement des Runners)
        // Base de la pile : Le TerminalRunner (exécute la stratégie)
        TerminalStationRunner terminalStationRunner = new TerminalStationRunner(strategyRegistry);
        StationRunner stack = terminalStationRunner;

        // On empile les extensions par dessus
        for (RuntimeExtension ext : extensions) {
            stack = ext.decorate(stack, ctx);
        }

        // 4. Le Sommet : ScopeInitializingRunner (Garantit la création du log/context)
        StationRunner rootRunner = new ScopeInitializingRunner(stack);
        
        // 5. Loopback pour la récursion
        terminalStationRunner.setRootRunner(rootRunner);

        return rootRunner;
    }
}
