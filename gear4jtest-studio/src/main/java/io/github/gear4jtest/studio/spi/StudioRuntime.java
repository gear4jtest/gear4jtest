package io.github.gear4jtest.studio.spi;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.ControlledRun;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.OperationDescriptor;
import io.github.gear4jtest.studio.model.RuntimeContext;
import io.github.gear4jtest.studio.model.ValidationReport;

/**
 * Host-owned application adapter. No Spring, XML or transport types cross this
 * contract.
 */
public interface StudioRuntime {
    RuntimeContext context();

    List<OperationDescriptor> catalog();

    void assertSafeToStore(ChainDefinition definition);

    /**
     * Admission of the entire unmodified source, including malformed input. Fail
     * closed when the adapter cannot classify all persisted values. This is a data
     * policy check, not a syntax or execution validation.
     */
    default void assertSafeSourceToStore(DraftRevision.Source source) {
        throw new io.github.gear4jtest.studio.service.StudioProblem(
                io.github.gear4jtest.studio.service.StudioProblem.Code.SOURCE_NOT_ADMITTED);
    }

    /** Cheap structural analysis; adapters may override to avoid compilation. */
    default ValidationReport analyze(ChainDefinition definition) {
        return validate(definition);
    }

    ValidationReport validate(ChainDefinition definition);

    String exportDefinition(ChainDefinition definition, String mediaType);

    /** Producer semantics, separate from the immutable bytes and host build. */
    default FormatAdapter formatAdapter(String mediaType) {
        return new FormatAdapter("unknown", "unknown");
    }

    record FormatAdapter(String id, String version) {
        public FormatAdapter {
            if (id == null || version == null || !id.matches("[A-Za-z0-9._-]{1,128}")
                    || !version.matches("[A-Za-z0-9._-]{1,64}"))
                throw new IllegalArgumentException("Invalid format adapter identity");
        }
    }

    ChainDefinition importDefinition(String source, String mediaType);

    ControlledRun execute(DraftRevision revision, String input, UUID requestId, String actor);

    /** Optional progress snapshots; adapters may emit only the final result. */
    default ControlledRun executeObserved(DraftRevision revision,
                                          String input,
                                          UUID requestId,
                                          String actor,
                                          java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> observer) {
        var run = execute(revision, input, requestId, actor);
        observer.accept(io.github.gear4jtest.studio.model.RunObservation.completed(run));
        return run;
    }

    default ControlledRun executeVersionObserved(io.github.gear4jtest.studio.model.StudioVersion version,
                                                 String input,
                                                 UUID requestId,
                                                 String actor,
                                                 java.util.function.Consumer<io.github.gear4jtest.studio.model.RunObservation> observer) {
        var run = executeVersion(version, input, requestId, actor);
        observer.accept(io.github.gear4jtest.studio.model.RunObservation.completed(run));
        return run;
    }

    /** Test the exact pinned artifact; never silently reserialize it. */
    default ControlledRun executeVersion(io.github.gear4jtest.studio.model.StudioVersion version,
                                         String input,
                                         UUID requestId,
                                         String actor) {
        throw new io.github.gear4jtest.studio.service.StudioProblem(
                io.github.gear4jtest.studio.service.StudioProblem.Code.FORBIDDEN);
    }
}
