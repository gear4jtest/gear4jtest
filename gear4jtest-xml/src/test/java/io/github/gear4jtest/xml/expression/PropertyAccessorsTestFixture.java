package io.github.gear4jtest.xml.expression;

public final class PropertyAccessorsTestFixture {
    private final Throwable failure;

    PropertyAccessorsTestFixture(Throwable failure) {
        this.failure = failure;
    }

    public String getValue() {
        if (failure instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) failure;
    }
}
