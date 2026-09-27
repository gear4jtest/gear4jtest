package io.github.gear4jtest.studio.demo;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.engine.AssemblyLineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.DataContract;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import io.github.gear4jtest.studio.runtime.OperationCatalog;
import io.github.gear4jtest.studio.runtime.RegisteredOperation;
import io.github.gear4jtest.studio.service.InMemoryStudioService;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.spi.StudioAuthorization;

public final class DemoWiring {
    public static final Map<String, String> SYNTHETIC_RESOURCES = Map.of("demo/suffix", " [synthetic resource]");

    private DemoWiring() {
    }

    public static OperationCatalog catalog() {
        return new OperationCatalog(List.of(
                                            operation("text.trim", "Nettoyer les espaces",
                                                      "Retire les espaces aux extrémités.", TrimOperation.class,
                                                      Map.of(), Map.of(), true),
                                            operation("text.uppercase", "Passer en majuscules",
                                                      "Transforme le texte en majuscules.", UppercaseOperation.class,
                                                      Map.of(), Map.of(), true),
                                            operation("text.prefix", "Ajouter un préfixe",
                                                      "Ajoute un texte public avant l’entrée.", PrefixOperation.class,
                                                      Map.of("prefix",
                                                             new OperationDescriptor.Parameter("Préfixe public",
                                                                     ParameterValue.Kind.TEXT, true, List.of())),
                                                      Map.of("prefix", "getPrefix"), true),
                                            operation("text.resource", "Ajouter une ressource",
                                                      "Résout une référence autorisée ; aucune capture des données du run.",
                                                      ResourceSuffixOperation.class,
                                                      Map.of("resource",
                                                             new OperationDescriptor.Parameter("Référence",
                                                                     ParameterValue.Kind.RESOURCE_REFERENCE, true,
                                                                     List.of("demo/suffix"))),
                                                      Map.of("resource", "getResource"), true),
                                            operation("production.only", "Opération de production",
                                                      "Exemple de capability interdite aux tests.",
                                                      ProductionOnlyOperation.class, Map.of(), Map.of(), false)));
    }

    private static RegisteredOperation operation(String id,
                                                 String name,
                                                 String description,
                                                 Class<? extends Operator<?, ?>> type,
                                                 Map<String, OperationDescriptor.Parameter> parameters,
                                                 Map<String, String> getters,
                                                 boolean testAllowed) {
        return new RegisteredOperation(
                new OperationDescriptor(id, name, description,
                        testAllowed ? "Calcul local sur données synthétiques" : "Interdite en TEST",
                        DataContract.text(), DataContract.text(), testAllowed, parameters),
                type, String.class, String.class, getters);
    }

    public static ResourceFactory plainResources() {
        Map<Class<?>, Supplier<?>> factories = Map.of(TrimOperation.class, TrimOperation::new, UppercaseOperation.class,
                                                      UppercaseOperation::new, PrefixOperation.class,
                                                      PrefixOperation::new, ResourceSuffixOperation.class,
                                                      () -> new ResourceSuffixOperation(SYNTHETIC_RESOURCES),
                                                      ProductionOnlyOperation.class, ProductionOnlyOperation::new);
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                var factory = factories.get(type);
                return factory == null ? null : type.cast(factory.get());
            }
        };
    }

    public static AssemblyLineExecutor plainExecutor(ResourceFactory resources) {
        return AssemblyLineEngine.builder().resourceFactory(resources)
                .extensionResolver(new RuntimeExtensionResolver(List.of()))
                .executionContextRegistry(new ExecutionContextRegistry()).build();
    }

    public static LocalGear4jRuntime runtime(String host,
                                             AssemblyLineExecutor executor,
                                             ResourceFactory resources,
                                             DataCapturePolicy capture) {
        return new LocalGear4jRuntime("studio-p0." + host, "studio-p0-20260926", catalog(), executor, resources,
                capture, Duration.ofSeconds(10));
    }

    public static StudioAuthorization authorization() {
        return (actor, action) -> {
            if (!"demo-editor".equals(actor)
                    && !("demo-viewer".equals(actor) && action == StudioAuthorization.Action.READ))
                throw new SecurityException("Action denied");
        };
    }

    public static InMemoryStudioService service(LocalGear4jRuntime runtime) {
        return new InMemoryStudioService(runtime, authorization());
    }

    public static ChainDefinition sample() {
        return new ChainDefinition(1, "text_preparation", "java.lang.String", "java.lang.String",
                List.of(new ChainDefinition.Operation("trim", "text.trim", Map.of()),
                        new ChainDefinition.Operation("uppercase", "text.uppercase", Map.of())));
    }

    public static DataCapturePolicy capturePolicy(String setting) {
        if (setting == null || setting.equals("none"))
            return DataCapturePolicy.none();
        if (setting.equals("masked"))
            return DataCapturePolicy.masked(value -> "[masqué]");
        if (setting.equals("synthetic"))
            return DataCapturePolicy.allowed();
        throw new IllegalArgumentException("Unknown capture policy");
    }
}
