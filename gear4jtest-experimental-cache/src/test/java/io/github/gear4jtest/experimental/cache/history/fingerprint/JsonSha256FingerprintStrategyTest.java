package io.github.gear4jtest.experimental.cache.history.fingerprint;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class JsonSha256FingerprintStrategyTest {
    private static final FingerprintContext CONTEXT = new FingerprintContext("line", "1.0.0");

    @Test
    void defaultMapper_shouldProduceTheSameFingerprintForDifferentMapInsertionOrders() {
        JsonSha256FingerprintStrategy<Map<String, Integer>> strategy = new JsonSha256FingerprintStrategy<>();
        Map<String, Integer> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Integer> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertThat(strategy.fingerprint(first, CONTEXT))
                .containsExactly(strategy.fingerprint(second, CONTEXT));
    }

    @Test
    void customMapperConstructor_shouldRejectNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JsonSha256FingerprintStrategy<>(null))
                .withMessage("mapper must not be null");
    }

    @Test
    void customMapperConstructor_shouldRemainUsableFromThePublishedApi() {
        JsonSha256FingerprintStrategy<String> strategy = new JsonSha256FingerprintStrategy<>(new ObjectMapper());

        assertThat(strategy.fingerprint("value", CONTEXT)).hasSize(32);
    }
}
