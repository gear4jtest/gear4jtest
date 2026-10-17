package io.github.gear4jtest.studio.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.github.gear4jtest.external.api.AssemblyLineManager;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.exception.PolicyViolationException;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.studio.model.PublishedArtifact;
import io.github.gear4jtest.studio.model.StudioPublication;
import io.github.gear4jtest.studio.model.StudioVersion;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioPublisher;
import io.github.gear4jtest.xml.generator.XmlToJavaGenerator;
import io.github.gear4jtest.xml.parser.XmlAssemblyLineParser;

/**
 * Uses the existing Gear4J publication, promotion and latest invalidation
 * mechanisms.
 */
public final class Gear4jPublicationAdapter implements StudioPublisher {
    private final AssemblyLineManager manager;
    private final OperationChainObjectRepository objects;
    private final StudioXmlAdapter xml;
    private final Consumer<String> prepareConfiguration;

    /**
     * All collaborators remain host-owned. Configuration must not replace an
     * existing host policy.
     */
    public Gear4jPublicationAdapter(AssemblyLineManager manager,
                                    OperationChainObjectRepository objects,
                                    OperationCatalog catalog,
                                    Consumer<String> prepareConfiguration) {
        this.manager = java.util.Objects.requireNonNull(manager);
        this.objects = java.util.Objects.requireNonNull(objects);
        this.xml = new StudioXmlAdapter(catalog);
        this.prepareConfiguration = java.util.Objects.requireNonNull(prepareConfiguration);
    }

    @Override
    public Set<String> capabilities() {
        return Set.of("PUBLISH_TEST", "PROMOTE_RUN");
    }

    private void candidate(StudioVersion version, String actor) {
        if (actor == null || actor.length() > 200)
            throw new StudioProblem(StudioProblem.Code.PUBLICATION_REJECTED);
        try {
            checkCandidate(version);
        } catch (StudioProblem | SecurityException invalid) {
            throw new StudioProblem(StudioProblem.Code.PUBLICATION_REJECTED);
        }
    }

    private void checkCandidate(StudioVersion version) {
        if (!version.mediaType().equals("application/xml")
                || !OperationCatalog.hash(version.source()).equals(version.artifactHash())
                || !xml.read(version.source()).id().equals(version.runtimeAssemblyLineId()))
            throw new StudioProblem(StudioProblem.Code.PUBLICATION_REJECTED);
    }

    @Override
    public void publishTest(StudioVersion version, String actor) {
        candidate(version, actor);
        prepareConfiguration.accept(version.runtimeAssemblyLineId());
        try {
            manager.registerAssemblyLine(version.runtimeAssemblyLineId(), version.label(), ExecutionMode.TEST,
                                         version.source().getBytes(StandardCharsets.UTF_8), version.mediaType(),
                                         List.of(), actor);
        } catch (PolicyViolationException rejected) {
            throw new StudioProblem(StudioProblem.Code.PUBLICATION_REJECTED);
        } catch (IOException failure) {
            throw new StudioProblem(StudioProblem.Code.STORAGE_UNAVAILABLE);
        }
    }

    @Override
    public void promoteToRun(StudioVersion version, String actor) {
        candidate(version, actor);
        try {
            manager.promoteTestToRun(version.runtimeAssemblyLineId(), version.label(), actor);
        } catch (PolicyViolationException rejected) {
            throw new StudioProblem(StudioProblem.Code.PUBLICATION_REJECTED);
        }
    }

    @Override
    public Optional<PublishedArtifact> find(StudioVersion version, StudioPublication.Mode mode) {
        return objects.find(version.runtimeAssemblyLineId(), version.label(), ExecutionMode.valueOf(mode.name()))
                .map(value -> new PublishedArtifact(value.alId(), value.version(), mode, value.contentHash(),
                        value.mimeType(), value.sizeBytes(), value.createdBy(), value.publishedAt()));
    }

    /**
     * The same explicit manifest supplies metadata, parameter expressions and
     * TEST/RUN policies.
     */
    public static OperationChainTranslator translator(OperationCatalog catalog) {
        var xml = new StudioXmlAdapter(catalog);
        return new OperationChainTranslator() {
            @Override
            public String id() {
                return "gear4j-studio-declarative-v2";
            }

            @Override
            public boolean supports(String mediaType) {
                return "application/xml".equals(mediaType);
            }

            @Override
            public GenerationResult translate(byte[] content, String mediaType) {
                return translate(content, mediaType, ExecutionMode.RUN);
            }

            @Override
            public GenerationResult translate(byte[] content, String mediaType, ExecutionMode mode) {
                if (!supports(mediaType) || content.length > StudioXmlAdapter.MAX_BYTES)
                    throw new StudioProblem(StudioProblem.Code.UNSUPPORTED_FORMAT);
                xml.read(new String(content, StandardCharsets.UTF_8));
                var definition = new XmlAssemblyLineParser(StudioXmlAdapter.MAX_BYTES)
                        .parse(new ByteArrayInputStream(content));
                var generated = XmlToJavaGenerator.builder("io.github.gear4jtest.studio.generated")
                        .classLoader(Gear4jPublicationAdapter.class.getClassLoader())
                        .sourcePolicy(catalog.sourcePolicy(mode))
                        .operatorCapabilityPolicy(catalog.capabilityPolicy()).build()
                        .generate(ModeBindings.bind(definition, catalog, mode), mode);
                return new GenerationResult(generated.className(), generated.formattedSource());
            }
        };
    }
}
