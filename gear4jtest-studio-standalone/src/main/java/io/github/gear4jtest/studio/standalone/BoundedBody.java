package io.github.gear4jtest.studio.standalone;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Cancels the upstream subscription before allocating an oversized response.
 */
final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> completed = new CompletableFuture<>();
    private Flow.Subscription subscription;

    BoundedBody(int limit) {
        this.limit = limit;
    }

    public CompletionStage<byte[]> getBody() {
        return completed;
    }

    public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        value.request(1);
    }

    public void onNext(List<ByteBuffer> buffers) {
        for (var buffer : buffers) {
            if (buffer.remaining() > limit - bytes.size()) {
                subscription.cancel();
                completed.completeExceptionally(new IllegalArgumentException("Response limit"));
                return;
            }
            var chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            bytes.writeBytes(chunk);
        }
        subscription.request(1);
    }

    public void onError(Throwable error) {
        completed.completeExceptionally(error);
    }

    public void onComplete() {
        completed.complete(bytes.toByteArray());
    }
}
