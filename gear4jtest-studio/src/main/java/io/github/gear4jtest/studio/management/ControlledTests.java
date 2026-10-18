package io.github.gear4jtest.studio.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();
    private final SecretKey key;
    private final Clock clock;
    private final TestReceiptPolicy policy;

    ControlledTests() {
        this(Clock.systemUTC(), TestReceiptPolicy.defaults());
    }

    ControlledTests(Clock clock, TestReceiptPolicy policy) {
        this.clock = java.util.Objects.requireNonNull(clock);
        this.policy = java.util.Objects.requireNonNull(policy);
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

    synchronized UUID scenarioResourceId(String actor, UUID requestId) {
        return receipt(actor, requestId).scenarioResourceId;
    }

    private Receipt receipt(String actor, UUID requestId) {
        expire();
        var value = receipts.get(requestId);
        if (value == null || !actor.equals(value.actor))
            throw new StudioProblem(NOT_FOUND);
        touch(requestId, value);
        return value;
    }

    private void expire() {
        var now = clock.instant();
        receipts.values().removeIf(r -> r.expiresAt != null && !now.isBefore(r.expiresAt));
    }

    private void touch(UUID requestId, Receipt receipt) {
        receipts.remove(requestId);
        receipts.put(requestId, receipt);
    }

    private void makeRoom() {
        if (receipts.size() < policy.capacity())
            return;
        var oldest = receipts.entrySet().iterator();
        while (oldest.hasNext()) {
            if (oldest.next().getValue().submission.state() != TestSubmission.State.RUNNING) {
                oldest.remove();
                return;
            }
        }
        throw new StudioProblem(CAPACITY_EXCEEDED);
    }

    TestSubmission execute(StudioIdentity actor,
                           DraftRevision revision,
                           UUID requestId,
                           String input,
                           StudioRuntime runtime,
                           Runnable admission) {
        return execute(actor, revision.draftId(), revision.revisionId(), requestId, input,
                       () -> runtime.execute(revision, input, requestId, actor.principalId()), admission);
    }

    TestSubmission execute(StudioIdentity actor,
                           UUID resourceId,
                           UUID contentId,
                           UUID requestId,
                           String input,
                           java.util.function.Supplier<io.github.gear4jtest.studio.model.ControlledRun> execution,
                           Runnable admission) {
        return execute(actor, resourceId, contentId, requestId, input, execution, admission, null);
    }

    TestSubmission execute(StudioIdentity actor,
                           UUID resourceId,
                           UUID contentId,
                           UUID requestId,
                           String input,
                           java.util.function.Supplier<io.github.gear4jtest.studio.model.ControlledRun> execution,
                           Runnable admission,
                           UUID scenarioResourceId) {
        if (requestId == null || input == null || input.length() > 8192)
            throw new StudioProblem(INVALID_REQUEST);
        Receipt receipt;
        synchronized (this) {
            expire();
            byte[] digest = fingerprint(actor.principalId(), contentId, input);
            receipt = receipts.get(requestId);
            if (receipt != null) {
                if (!actor.principalId().equals(receipt.actor) || !MessageDigest.isEqual(digest, receipt.fingerprint))
                    throw new StudioProblem(CONFLICT);
                touch(requestId, receipt);
                return receipt.submission;
            }
            makeRoom();
            receipt = new Receipt(actor.principalId(), resourceId, digest,
                    new TestSubmission(requestId, TestSubmission.State.RUNNING, null, null), scenarioResourceId);
            receipts.put(requestId, receipt);
        }
        TestSubmission result;
        try {
            admission.run();
            result = new TestSubmission(requestId, TestSubmission.State.COMPLETED,
                    execution.get(), null);
        } catch (StudioProblem e) {
            boolean uncertain = e.code() == STORAGE_UNAVAILABLE;
            result = new TestSubmission(requestId,
                    uncertain ? TestSubmission.State.OUTCOME_UNKNOWN : TestSubmission.State.PREPARATION_FAILED, null,
                    uncertain ? "TEST_OUTCOME_UNKNOWN"
                            : e.code() == UNSUPPORTED_CONTRACT ? "UNSUPPORTED_CONTRACT" : "TEST_NOT_ADMITTED");
        } catch (RuntimeException e) {
            result = new TestSubmission(requestId, TestSubmission.State.OUTCOME_UNKNOWN, null, "TEST_OUTCOME_UNKNOWN");
        }
        synchronized (this) {
            receipt.submission = result;
            receipt.expiresAt = clock.instant().plus(policy.terminalTtl());
            touch(requestId, receipt);
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
        private final UUID scenarioResourceId;
        private final byte[] fingerprint;
        private TestSubmission submission;
        private Instant expiresAt;

        private Receipt(String actor,
                        UUID draftId,
                        byte[] fingerprint,
                        TestSubmission submission,
                        UUID scenarioResourceId) {
            this.scenarioResourceId = scenarioResourceId;
            this.actor = actor;
            this.draftId = draftId;
            this.fingerprint = fingerprint;
            this.submission = submission;
        }
    }
}
