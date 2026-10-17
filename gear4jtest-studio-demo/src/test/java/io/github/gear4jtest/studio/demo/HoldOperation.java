package io.github.gear4jtest.studio.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;

public final class HoldOperation implements Operator<String, String> {
    private final CountDownLatch started;
    private final CountDownLatch release;

    public HoldOperation(CountDownLatch started, CountDownLatch release) {
        this.started = started;
        this.release = release;
    }

    @Override
    public String transform(String input, StationExecutionContext context) {
        started.countDown();
        try {
            if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Synthetic deadline");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
        return input;
    }
}
