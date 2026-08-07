package io.github.gear4jtest.external.api;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test hook called from an isolated generated classloader. */
public final class GeneratedLoadingTestHooks {
    private static volatile CountDownLatch constructorEntered;
    private static volatile CountDownLatch releaseConstructor;

    private GeneratedLoadingTestHooks() {
    }

    static void installConstructorBlock(CountDownLatch entered, CountDownLatch release) {
        constructorEntered = entered;
        releaseConstructor = release;
    }

    static void clearConstructorBlock() {
        constructorEntered = null;
        releaseConstructor = null;
    }

    public static void awaitInConstructor() {
        CountDownLatch entered = constructorEntered;
        CountDownLatch release = releaseConstructor;
        if (entered == null || release == null) {
            throw new IllegalStateException("constructor block is not installed");
        }
        entered.countDown();
        boolean released = false;
        while (!released) {
            try {
                released = release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Deliberately non-cooperative generated constructor.
            }
        }
    }
}
