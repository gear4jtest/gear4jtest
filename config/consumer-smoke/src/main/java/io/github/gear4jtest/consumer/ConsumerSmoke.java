package io.github.gear4jtest.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.jackson.JacksonPayloadCloners;
import io.github.gear4jtest.xml.translator.XmlOperationChainTranslator;

/** Executable smoke test for staged Gear4J artifacts. */
public final class ConsumerSmoke {
    private ConsumerSmoke() {
    }

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        PayloadCloner payloadCloner = JacksonPayloadCloners.with(objectMapper);
        OperationChainTranslator translator = XmlOperationChainTranslator.gelOnly();

        if (payloadCloner == null || !translator.supports(XmlOperationChainTranslator.VENDOR_MEDIA_TYPE)) {
            throw new IllegalStateException("Staged Gear4J artifacts are not usable");
        }
    }
}
