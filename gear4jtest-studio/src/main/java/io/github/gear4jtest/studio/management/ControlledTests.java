package io.github.gear4jtest.studio.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import io.github.gear4jtest.studio.model.DraftRevision;
import io.github.gear4jtest.studio.model.TestSubmission;
import io.github.gear4jtest.studio.service.StudioProblem;
import io.github.gear4jtest.studio.spi.StudioRuntime;

import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

/**
 * Process-local receipts, deliberately separate from durable management
 * transactions.
 */
final class ControlledTests {
    private final Map<UUID, Receipt> receipts = new HashMap<>();
    private final SecretKey key;

    ControlledTests() {
        try {
            key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    synchronized UUID resourceId(String actor, UUID requestId) {
        return receipt(actor, requestId).draftId;
    }

    synchronized TestSubmission status(String actor, UUID requestId) {
        return receipt(actor, requestId).submission;
    }

    private Receipt receipt(String actor, UUID requestId) {
        var value = receipts.get(requestId);
        if (value == null || !actor.equals(value.actor))
            throw new StudioProblem(NOT_FOUND);
        return value;
    }

    TestSubmission execute(StudioIdentity actor,
                           DraftRevision revision,
                           UUID requestId,
                           String input,
                           StudioRuntime runtime,
                           Runnable admission) {
        return execute(actor, revision.draftId(), revision.revisionId(), requestId, input,
                       () -> runtime.execute(revision, input, requestId, actor.subject()), admission);
    }

    TestSubmission execute(StudioIdentity actor,
                           UUID resourceId,
                           UUID contentId,
                           UUID requestId,
                           String input,
                           java.util.function.Supplier<io.github.gear4jtest.studio.model.ControlledRun> execution,
                           Runnable admission) {
        if (requestId == null || input == null || input.length() > 8192)
            throw new StudioProblem(INVALID_REQUEST);
        Receipt receipt;
        synchronized (this) {
            byte[] digest = fingerprint(actor.subject(), contentId, input);
            receipt = receipts.get(requestId);
            if (receipt != null) {
                if (!actor.subject().equals(receipt.actor) || !MessageDigest.isEqual(digest, receipt.fingerprint))
                    throw new StudioProblem(CONFLICT);
                return receipt.submission;
            }
            if (receipts.size() >= 128)
                throw new StudioProblem(CAPACITY_EXCEEDED);
            receipt = new Receipt(actor.subject(), resourceId, digest,
                    new TestSubmission(requestId, TestSubmission.State.RUNNING, null, null));
            receipts.put(requestId, receipt);
        }
        TestSubmission result;
        try {
            admission.run();
            result = new TestSubmission(requestId, TestSubmission.State.COMPLETED,
                    execution.get(), null);
        } catch (StudioProblem e) {
            result = new TestSubmission(requestId, TestSubmission.State.PREPARATION_FAILED, null,
                    e.code() == UNSUPPORTED_CONTRACT ? "UNSUPPORTED_CONTRACT" : "TEST_NOT_ADMITTED");
        } catch (RuntimeException e) {
            result = new TestSubmission(requestId, TestSubmission.State.OUTCOME_UNKNOWN, null, "TEST_OUTCOME_UNKNOWN");
        }
        synchronized (this) {
            receipt.submission = result;
            return result;
        }
    }

    private byte[] fingerprint(String actor, UUID revision, String input) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal((actor.length() + ":" + actor + ":" + revision + ":" + input)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static final class Receipt {
        private final String actor;
        private final UUID draftId;
        private final byte[] fingerprint;
        private TestSubmission submission;

        private Receipt(String actor, UUID draftId, byte[] fingerprint, TestSubmission submission) {
            this.actor = actor;
            this.draftId = draftId;
            this.fingerprint = fingerprint;
            this.submission = submission;
        }
    }
}
