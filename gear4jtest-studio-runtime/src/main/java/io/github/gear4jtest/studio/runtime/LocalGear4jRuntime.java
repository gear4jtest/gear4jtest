package io.github.gear4jtest.studio.runtime;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.studio.model.CapturedValue;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ControlledRun;
import io.github.gear4jtest.studio.model.Diagnostic;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.studio.model.ParameterValue;
import io.github.gear4jtest.studio.model.RuntimeContext;
import io.github.gear4jtest.studio.model.ValidationReport;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import io.github.gear4jtest.studio.spi.StudioRuntime;
import io.github.gear4jtest.xml.generator.XmlToJavaGenerator;
import io.github.gear4jtest.xml.parser.XmlAssemblyLineParser;
import io.github.gear4jtest.xml.validator.AssemblyLineValidator;

/**
 * Local P0 adapter: compile, then execute a pinned draft without publishing it.
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
            throw new IllegalArgumentException("Deadline outside P0 limits");
        this.catalog = catalog;
        this.executor = executor;
        this.resources = resources;
        this.capture = capture;
        this.deadline = deadline;
        checks = new DefinitionChecks(catalog);
        xml = new StudioXmlAdapter(catalog);
        context = new RuntimeContext(id, applicationBuild, catalog.fingerprint(),
                Set.of("DRAFT", "VALIDATE", "CONTROLLED_TEST", "XML_SUBSET"));
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
    public String exportDefinition(ChainDefinition definition, String mediaType) {
        format(mediaType);
        return xml.write(definition);
    }

    @Override
    public ChainDefinition importDefinition(String source, String mediaType) {
        format(mediaType);
        return xml.read(source);
    }

    private void format(String mediaType) {
        if (!"application/xml".equals(mediaType))
            throw new StudioProblem(StudioProblem.Code.UNSUPPORTED_FORMAT);
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
        String source = xml.write(definition);
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        new AssemblyLineValidator(StudioXmlAdapter.MAX_BYTES).validate(bytes);
        var parsed = new XmlAssemblyLineParser(StudioXmlAdapter.MAX_BYTES).parse(new ByteArrayInputStream(bytes));
        ClassLoader loader = getClass().getClassLoader();
        var generated = XmlToJavaGenerator.builder("io.github.gear4jtest.studio.generated").classLoader(loader)
                .sourcePolicy(catalog.sourcePolicy()).operatorCapabilityPolicy(catalog.capabilityPolicy()).build()
                .generate(parsed, ExecutionMode.TEST);
        var classes = new JavaxToolsGeneratedSourceCompiler(loader)
                .compile(generated.className(), generated.formattedSource().getBytes(StandardCharsets.UTF_8));
        return new Compiled(generated.className(), classes, OperationCatalog.hash(source));
    }

    @Override
    public ControlledRun execute(DraftRevision revision, String input, UUID requestId, String actor) {
        if (!new ValidationReport(catalog.fingerprint(), checks.validate(revision.definition())).valid())
            throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
        acquire();
        try {
            Compiled compiled;
            AssemblyLine<String, String> line;
            try {
                compiled = compile(revision.definition());
                line = instantiate(compiled);
            } catch (Exception e) {
                throw new StudioProblem(StudioProblem.Code.INVALID_DEFINITION);
            }
            var observer = new StepObserver();
            var token = new CancellationToken();
            Instant started = Instant.now();
            var timer = deadlines.schedule(() -> token.cancel("Studio test deadline"), deadline.toMillis(),
                                           TimeUnit.MILLISECONDS);
            try {
                var result = executor.execute(line, RunRequest.builder().input(input).resourceFactory(resources)
                        .cancellationToken(token).with(observer).build());
                boolean forbidden = revision.definition().nodes().stream().anyMatch(this::containsReference);
                return new ControlledRun(requestId,
                        result.getExecution() == null ? null : result.getExecution().getId(), revision.draftId(),
                        revision.revisionId(), revision.number(), actor, context.id(), context.applicationBuild(),
                        "TEST", catalog.fingerprint(), compiled.hash(), result.getOutcome().name(), started,
                        Instant.now(),
                        forbidden ? CapturedValue.none() : capture(DataCapturePolicy.Target.INPUT, input),
                        forbidden ? CapturedValue.none() : capture(DataCapturePolicy.Target.OUTPUT, result.getResult()),
                        result.getError() == null ? null : "EXECUTION_ERROR", observer.snapshot());
            } finally {
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

    private CapturedValue capture(DataCapturePolicy.Target target, String value) {
        try {
            var captured = capture.capture(target, value);
            return captured == null || captured.value() != null && captured.value().length() > 8192
                    ? CapturedValue.none() : captured;
        } catch (RuntimeException e) {
            return CapturedValue.none();
        }
    }

    private void acquire() {
        if (closed || !slot.tryAcquire())
            throw new StudioProblem(StudioProblem.Code.CAPACITY_EXCEEDED);
    }

    @Override
    public void close() {
        closed = true;
        deadlines.shutdownNow();
    }

    private record Compiled(String className, Map<String, byte[]> classes, String hash) {}

    private static final class StepObserver implements StationLifecycleExtension {
        private final List<ControlledRun.Step> steps = Collections.synchronizedList(new ArrayList<>());

        private void add(StationLogRecord record) {
            steps.add(new ControlledRun.Step(record.id(), record.operationId(), record.status().name(),
                    record.startedAt(), record.endedAt()));
        }

        private List<ControlledRun.Step> snapshot() {
            synchronized (steps) {
                return List.copyOf(steps);
            }
        }

        @Override
        public void onStationCompleted(ExecutionContext run, StationExecutionContext station, StationLogRecord record) {
            add(record);
        }

        @Override
        public void onStationSkipped(ExecutionContext run,
                                     StationExecutionContext station,
                                     StationLogRecord record,
                                     StationSkipReason reason) {
            add(record);
        }

        @Override
        public void onStationCancelled(ExecutionContext run,
                                       StationExecutionContext station,
                                       StationLogRecord record,
                                       StationCancellationReason reason,
                                       Exception error) {
            add(record);
        }

        @Override
        public void onStationInterrupted(ExecutionContext run,
                                         StationExecutionContext station,
                                         StationLogRecord record,
                                         StationInterruptionReason reason,
                                         String by,
                                         Exception error) {
            add(record);
        }

        @Override
        public void onStationFailedBeforeStart(ExecutionContext run,
                                               StationExecutionContext station,
                                               StationLogRecord record,
                                               Exception error) {
            add(record);
        }
    }
}
