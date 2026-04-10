package io.github.gear4jtest.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.context.PayloadCloner;

public final class JacksonPayloadCloners {

    private JacksonPayloadCloners() {
        // Utility class
    }

    public static PayloadCloner with(ObjectMapper objectMapper) {
        return new JacksonPayloadCloner(objectMapper);
    }

    public static PayloadCloner defaultMapper() {
        return new JacksonPayloadCloner(new ObjectMapper().findAndRegisterModules());
    }
}
