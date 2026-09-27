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

    ValidationReport validate(ChainDefinition definition);

    String exportDefinition(ChainDefinition definition, String mediaType);

    ChainDefinition importDefinition(String source, String mediaType);

    ControlledRun execute(DraftRevision revision, String input, UUID requestId, String actor);
}
