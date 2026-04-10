package io.github.gear4jtest.core.api.context;

/**
 * Factory methods for built-in {@link PayloadCloner} implementations.
 */
public final class PayloadCloners {

    private static final PayloadCloner IMMUTABLE_AWARE = new ImmutableAwarePayloadCloner();
    private static final PayloadCloner NO_OP_UNSAFE = new NoOpPayloadCloner();

    private PayloadCloners() {
        // Utility class
    }

    /**
     * Returns the default strict payload cloner.
     *
     * <p>This implementation only shares references for known immutable payloads and fails
     * explicitly for any other type.
     */
    public static PayloadCloner immutableAware() {
        return IMMUTABLE_AWARE;
    }

    /**
     * Returns an unsafe payload cloner that never clones and always returns the original
     * instance.
     *
     * <p>This should only be used intentionally in legacy scenarios where the caller fully
     * controls payload mutability.
     */
    public static PayloadCloner noOpUnsafe() {
        return NO_OP_UNSAFE;
    }
}
