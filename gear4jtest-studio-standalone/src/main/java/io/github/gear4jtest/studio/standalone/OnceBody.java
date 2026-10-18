package io.github.gear4jtest.studio.standalone;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refuses a second body emission even if the underlying HTTP client attempts a
 * retry.
 */
final class OnceBody implements HttpRequest.BodyPublisher {
    private final HttpRequest.BodyPublisher delegate;
    private final AtomicBoolean subscribed = new AtomicBoolean();

    OnceBody(byte[] body) {
        delegate = HttpRequest.BodyPublishers.ofByteArray(body);
    }

    public long contentLength() {
        return delegate.contentLength();
    }

    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (subscribed.compareAndSet(false, true))
            delegate.subscribe(subscriber);
        else {
            subscriber.onSubscribe(new Flow.Subscription() {
                public void request(long count) {
                }

                public void cancel() {
                }
            });
            subscriber.onError(new IllegalStateException("Management body cannot be replayed"));
        }
    }
}
