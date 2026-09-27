package io.github.gear4jtest.studio.spi;

import java.util.function.UnaryOperator;

import io.github.gear4jtest.studio.model.CapturedValue;

@FunctionalInterface
public interface DataCapturePolicy {
    CapturedValue capture(Target target, String value);

    enum Target {
        INPUT, OUTPUT
    }

    static DataCapturePolicy none() {
        return (target, value) -> CapturedValue.none();
    }

    static DataCapturePolicy allowed() {
        return (target, value) -> new CapturedValue(CapturedValue.State.CAPTURED, value);
    }

    static DataCapturePolicy masked(UnaryOperator<String> redactor) {
        return (target, value) -> new CapturedValue(CapturedValue.State.REDACTED, redactor.apply(value));
    }
}
