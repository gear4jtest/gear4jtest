package io.github.gear4jtest.studio.runtime;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ControlledRun;
import io.github.gear4jtest.studio.model.Diagnostic;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.model.RuntimeContext;
import io.github.gear4jtest.studio.model.ScenarioCheck;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioInput;
import io.github.gear4jtest.studio.model.ValidationReport;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.spi.StudioRuntime;
import io.github.gear4jtest.xml.generator.XmlToJavaGenerator;
import io.github.gear4jtest.xml.parser.XmlAssemblyLineParser;
import io.github.gear4jtest.xml.validator.AssemblyLineValidator;

/**
 * Local adapter: compile, then execute a pinned draft without publishing it.
 * The deadline is cooperative.
 */
public final class LocalGear4jRuntime implements StudioRuntime, AutoCloseable {
    private final OperationCatalog catalog;
    private final DefinitionChecks checks;
    private final StudioXmlAdapter xml;
    private final RuntimeContext context;
    private final AssemblyLineExecutor executor;
    private final ResourceFactory resources;
    private final DataCapturePolicy capture;
    private final Duration deadline;
    private final Semaphore slot = new Semaphore(1);
    private final RunObservationDispatcher observations;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "studio-test-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public LocalGear4jRuntime(String id,
                              String applicationBuild,
                              OperationCatalog catalog,
                              AssemblyLineExecutor executor,
                              ResourceFactory resources,
                              DataCapturePolicy capture,
                              Duration deadline) {
        if (deadline.isZero() || deadline.isNegative() || deadline.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Deadline outside adapter limits");
        this.catalog = catalog;
        this.executor = executor;
        this.resources = resources;
        this.capture = capture;
        this.deadline = deadline;
        checks = new DefinitionChecks(catalog);
        xml = new StudioXmlAdapter(catalog);
        context = new RuntimeContext(id, applicationBuild, catalog.fingerprint(),
                Set.of("DRAFT", "VALIDATE", "CONTROLLED_TEST", "CONTROLLED_TEST_STRING", "CONTROLLED_TEST_VERSION",
                       "XML_SUBSET", "SCENARIO_TEST", "SIMULATION"));
        observations = new RunObservationDispatcher();
    }

    @Override
    public RuntimeContext context() {
        return context;
    }

    @Override
    public List<OperationDescriptor> catalog() {
        return catalog.descriptors();
    }

    @Override
    public void assertSafeToStore(ChainDefinition definition) {
        checks.safeToStore(definition);
    }

    @Override
    public void assertSafeSourceToStore(DraftRevision.Source source) {
        format(source.mediaType());
        new XmlSourceStoragePolicy(catalog).check(source.text());
    }

    @Override
    public String exportDefinition(ChainDefinition definition, String mediaType) {
        format(mediaType);
        return xml.write(definition);
    }

    @Override
    public ChainDefinition importDefinition(String source, String mediaType) {
        format(mediaType);
        return xml.read(source);
    }

    @Override
    public FormatAdapter formatAdapter(String mediaType) {
        format(mediaType);
        return new FormatAdapter("gear4j-studio-xml", "2");
    }

    private void format(String mediaType) {
        if (!"application/xml".equals(mediaType))
            throw new StudioProblem(StudioProblem.Code.UNSUPPORTED_FORMAT);
    }

    @Override
    public io.github.gear4jtest.studio.model.SimulationPlan planSimulation(ChainDefinition definition,
                                                                           ScenarioData data) {
        return new DeclarativeSimulator(catalog, capture).plan(definition, data);
    }

    @Override
    public io.github.gear4jtest.studio.model.SimulationResult simulate(ChainDefinition definition, ScenarioData data) {
        if (closed)
            throw new StudioProblem(StudioProblem.Code.CAPACITY_EXCEEDED);
        return new DeclarativeSimulator(catalog, capture).simulate(definition, data);
    }

    @Override
    public ValidationReport analyze(ChainDefinition definition) {
        return new ValidationReport(catalog.fingerprint(), checks.validate(definition));
    }

    @Override
    public ValidationReport validate(ChainDefinition definition) {
        var diagnostics = new ArrayList<>(checks.validate(definition));
        if (diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR)) {
            acquire();
            try {
                compile(definition);
            } catch (RuntimeException e) {
                diagnostics.add(Diagnostic.error("GEAR4J_COMPILATION_FAILED", "/",
                                                 "La compilation Gear4J a rejeté la définition."));
            } finally {
                slot.release();
            }
        }
        return new ValidationReport(catalog.fingerprint(), diagnostics);
    }

    private Compiled compile(ChainDefinition definition) {
        return compileSource(xml.write(definition));
    }

    private Compiled compileSource(String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        new AssemblyLineValidator(StudioXmlAdapter.MAX_BYTES).validate(bytes);
        var parsed = new XmlAssemblyLineParser(StudioXmlAdapter.MAX_BYTES).parse(new ByteArrayInputStream(bytes));
        ClassLoader loader = getClass().getClassLoader();
        var generated = XmlToJavaGenerator.builder("io.github.gear4jtest.studio.generated").classLoader(loader)
                .sourcePolicy(catalog.sourcePolicy(ExecutionMode.TEST))
                .operatorCapabilityPolicy(catalog.capabilityPolicy()).build()
                .generate(ModeBindings.bind(parsed, catalog, ExecutionMode.TEST), ExecutionMode.TEST);
        var classes = new JavaxToolsGeneratedSourceCompiler(loader)
                .compile(generated.className(), generated.formattedSource().getBytes(StandardCharsets.UTF_8));
        return new Compiled(generated.className(), classes, OperationCatalog.hash(source));
    }

    @Override
    public ControlledRun execute(DraftRevision revision, String input, UUID requestId, String actor) {
        return executeObserved(revision, input, requestId, actor, ignored -> {
        });
    }

    @Override
    public ControlledRun executeObserved(DraftRevision revision,
                                         String input,
                                         UUID requestId,
                                         String actor,
                                         java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> observer) {
        return executeSource(revision, input, requestId, actor, null, null, observer);
    }

    @Override
    public ControlledRun executeVersion(io.github.gear4jtest.studio.model.StudioVersion version,
                                        String input,
                                        UUID requestId,
                                        String actor) {
        return executeVersionObserved(version, input, requestId, actor, ignored -> {
        });
    }

    @Override
    public ControlledRun executeVersionObserved(io.github.gear4jtest.studio.model.StudioVersion version,
                                                String input,
                                                UUID requestId,
                                                String actor,
                                                java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> observer) {
        return executeVersionScenario(version, input, requestId, actor, null, observer);
    }

    @Override
    public ControlledRun executeScenario(DraftRevision revision,
                                         String input,
                                         UUID requestId,
                                         String actor,
                                         ScenarioData data,
                                         java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> observer) {
        return executeSource(revision, input, requestId, actor, null, data, observer);
    }

    @Override
    public ControlledRun executeVersionScenario(io.github.gear4jtest.studio.model.StudioVersion version,
                                                String input,
                                                UUID requestId,
                                                String actor,
                                                ScenarioData data,
                                                java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> observer) {
        format(version.mediaType());
        if (!version.runtimeContextId().equals(context.id())
                || !OperationCatalog.hash(version.source()).equals(version.artifactHash()))
            throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        var definition = xml.read(version.source());
        if (!definition.id().equals(version.runtimeAssemblyLineId()))
            throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        var revision = new DraftRevision(version.draftId(), version.revisionId(), version.revision(),
                version.author(), version.createdAt(), DraftRevision.Origin.STUDIO, definition);
        return executeSource(revision, input, requestId, actor, version.source(), data, observer).withVersion(version);
    }

    private ControlledRun executeSource(DraftRevision revision,
                                        String input,
                                        UUID requestId,
                                        String actor,
                                        String source,
                                        ScenarioData scenario,
                                        java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> progress) {
        revision.requireDefinition();
        if (!revision.definition().inputType().equals("java.lang.String")
                || !revision.definition().outputType().equals("java.lang.String"))
            throw new StudioProblem(StudioProblem.Code.UNSUPPORTED_CONTRACT);
        if (!new ValidationReport(catalog.fingerprint(), checks.validate(revision.definition())).valid())
            throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        acquire();
        try {
            Compiled compiled;
            AssemblyLine<String, String> line;
            try {
                compiled = source == null ? compile(revision.definition()) : compileSource(source);
                line = instantiate(compiled);
            } catch (Exception e) {
                throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
            }
            boolean forbidden = revision.definition().nodes().stream().anyMatch(this::containsReference)
                    || scenario != null && scenario.input().kind() == ScenarioInput.Kind.FIXTURE_REFERENCE;
            var observer = new Gear4jRunObserver(observations, progress, forbidden ? DataCapturePolicy.none() : capture,
                    compiled.hash(), input);
            var token = new CancellationToken();
            Instant started = Instant.now();
            var timer = deadlines.schedule(() -> token.cancel("Studio test deadline"), deadline.toMillis(),
                                           TimeUnit.MILLISECONDS);
            try {
                var result = executor.execute(line, RunRequest.builder().input(input).resourceFactory(resources)
                        .cancellationToken(token).with(observer).build());
                var check = scenario == null ? null
                        : ScenarioCheck.evaluate(scenario.expected(), result.getOutcome().name(), result.getResult());
                var observed = observer.finish(result);
                return new ControlledRun(requestId,
                        result.getExecution() == null ? null : result.getExecution().getId(), revision.draftId(),
                        revision.revisionId(), revision.number(), actor, context.id(), context.applicationBuild(),
                        "TEST", catalog.fingerprint(), compiled.hash(), result.getOutcome().name(),
                        observed.startedAt() == null ? started : observed.startedAt(), observed.endedAt(),
                        observed.input(), observed.output(), observed.errorCode(), observed.steps(), null, null,
                        observed.stepsTruncated()).withScenario(null, check);
            } finally {
                observer.close();
                timer.cancel(false);
            }
        } finally {
            slot.release();
        }
    }

    // Outer String contracts are checked before code generation; javac checks all
    // station links.
    @SuppressWarnings("unchecked")
    private AssemblyLine<String, String> instantiate(Compiled compiled) throws Exception {
        var loader = new InMemoryClassLoader(getClass().getClassLoader());
        loader.addCompiledClasses(compiled.classes());
        var instance = loader.createInstance(compiled.className());
        new SimpleDependencyInjector().injectDependencies(instance, ExecutionMode.TEST);
        return ((GeneratedAssemblyLine<String, String>) instance).getAssemblyLineDefinition();
    }

    private boolean containsReference(ChainDefinition.Node node) {
        if (node instanceof ChainDefinition.Operation operation)
            return operation.parameters().values().stream()
                    .anyMatch(p -> p.kind() == ParameterValue.Kind.RESOURCE_REFERENCE);
        var choice = (ChainDefinition.Choice) node;
        return containsReference(choice.whenTrue()) || containsReference(choice.whenFalse());
    }

    private void acquire() {
        if (closed || !slot.tryAcquire())
            throw new StudioProblem(StudioProblem.Code.CAPACITY_EXCEEDED);
    }

    @Override
    public void close() {
        closed = true;
        deadlines.shutdownNow();
        observations.close();
    }

    private record Compiled(String className, Map<String, byte[]> classes, String hash) {}

}
