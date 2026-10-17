package io.github.gear4jtest.studio.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.TestSubmission;
import io.github.gear4jtest.studio.model.ValidationReport;
import io.github.gear4jtest.studio.spi.StudioAuthorization;
import io.github.gear4jtest.studio.spi.StudioAuthorization.Action;
import io.github.gear4jtest.studio.spi.StudioRuntime;

import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

/**
 * Bounded, process-local P0 store. Drafts are private; restart loses all state
 * and idempotency receipts.
 */
public final class InMemoryStudioService {
    private final StudioRuntime runtime;
    private final StudioAuthorization authorization;
    private final Map<UUID, List<DraftRevision>> drafts = new HashMap<>();
    private final Map<UUID, Receipt> receipts = new HashMap<>();
    private final SecretKey key;

    public InMemoryStudioService(StudioRuntime runtime, StudioAuthorization authorization) {
        this.runtime = runtime;
        this.authorization = authorization;
        try {
            key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    public StudioRuntime runtime() {
        return runtime;
    }

    public synchronized DraftRevision create(String actor, ChainDefinition definition) {
        return create(actor, definition, DraftRevision.Origin.STUDIO);
    }

    private DraftRevision create(String actor, ChainDefinition definition, DraftRevision.Origin origin) {
        authorization.require(actor, Action.EDIT);
        runtime.assertSafeToStore(definition);
        if (drafts.size() >= 64)
            throw new StudioProblem(CAPACITY_EXCEEDED);
        var revision = new DraftRevision(UUID.randomUUID(), UUID.randomUUID(), 1, actor, Instant.now(), origin,
                definition);
        drafts.put(revision.draftId(), new ArrayList<>(List.of(revision)));
        return revision;
    }

    public synchronized DraftRevision importDraft(String actor, String source) {
        authorization.require(actor, Action.EDIT);
        return create(actor, runtime.importDefinition(source, "application/xml"), DraftRevision.Origin.XML_IMPORT);
    }

    public synchronized DraftRevision save(String actor, UUID id, long expectedRevision, ChainDefinition definition) {
        authorization.require(actor, Action.EDIT);
        var history = owned(actor, id);
        if (history.size() != expectedRevision)
            throw new StudioProblem(CONFLICT);
        runtime.assertSafeToStore(definition);
        if (history.size() >= 64)
            throw new StudioProblem(CAPACITY_EXCEEDED);
        var revision = new DraftRevision(id, UUID.randomUUID(), history.size() + 1L, actor, Instant.now(),
                history.get(0).origin(), definition);
        history.add(revision);
        return revision;
    }

    public synchronized DraftRevision draft(String actor, UUID id) {
        authorization.require(actor, Action.READ);
        var history = owned(actor, id);
        return history.get(history.size() - 1);
    }

    public synchronized List<DraftRevision> revisions(String actor, UUID id) {
        authorization.require(actor, Action.READ);
        return List.copyOf(owned(actor, id));
    }

    private List<DraftRevision> owned(String actor, UUID id) {
        var history = drafts.get(id);
        if (history == null || !history.get(0).author().equals(actor))
            throw new StudioProblem(NOT_FOUND);
        return history;
    }

    public ValidationReport validate(String actor, ChainDefinition definition) {
        authorization.require(actor, Action.VALIDATE);
        return runtime.validate(definition);
    }

    public String export(String actor, ChainDefinition definition) {
        authorization.require(actor, Action.READ);
        return runtime.exportDefinition(definition, "application/xml");
    }

    public TestSubmission test(String actor, UUID draftId, long number, UUID requestId, String input) {
        authorization.require(actor, Action.TEST);
        if (input == null || input.length() > 8192 || requestId == null)
            throw new StudioProblem(INVALID_DEFINITION);
        DraftRevision revision;
        Receipt receipt;
        synchronized (this) {
            var history = owned(actor, draftId);
            if (number < 1 || number > history.size())
                throw new StudioProblem(NOT_FOUND);
            revision = history.get((int) number - 1);
            byte[] fingerprint = fingerprint(actor, revision.revisionId(), input);
            receipt = receipts.get(requestId);
            if (receipt != null) {
                if (!receipt.actor.equals(actor) || !MessageDigest.isEqual(receipt.fingerprint, fingerprint))
                    throw new StudioProblem(CONFLICT);
                return receipt.submission;
            }
            if (receipts.size() >= 128)
                throw new StudioProblem(CAPACITY_EXCEEDED);
            receipt = new Receipt(actor, fingerprint,
                    new TestSubmission(requestId, TestSubmission.State.RUNNING, null, null));
            receipts.put(requestId, receipt);
        }
        TestSubmission result;
        try {
            result = new TestSubmission(requestId, TestSubmission.State.COMPLETED,
                    runtime.execute(revision, input, requestId, actor), null);
        } catch (StudioProblem e) {
            result = new TestSubmission(requestId, TestSubmission.State.PREPARATION_FAILED, null, "TEST_NOT_ADMITTED");
        } catch (RuntimeException e) {
            result = new TestSubmission(requestId, TestSubmission.State.OUTCOME_UNKNOWN, null, "TEST_OUTCOME_UNKNOWN");
        }
        synchronized (this) {
            receipt.submission = result;
            return result;
        }
    }

    public synchronized TestSubmission testRequest(String actor, UUID requestId) {
        authorization.require(actor, Action.READ);
        var receipt = receipts.get(requestId);
        if (receipt == null || !receipt.actor.equals(actor))
            throw new StudioProblem(NOT_FOUND);
        return receipt.submission;
    }

    private byte[] fingerprint(String actor, UUID revisionId, String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal((actor.length() + ":" + actor + ":" + revisionId + ":" + input)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static final class Receipt {
        private final String actor;
        private final byte[] fingerprint;
        private TestSubmission submission;

        private Receipt(String actor, byte[] fingerprint, TestSubmission submission) {
            this.actor = actor;
            this.fingerprint = fingerprint;
            this.submission = submission;
        }
    }
}
