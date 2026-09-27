package io.github.gear4jtest.studio.springdemo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.studio.demo.DemoWiring;
import io.github.gear4jtest.studio.demo.PrefixOperation;
import io.github.gear4jtest.studio.model.TestSubmission;
import io.github.gear4jtest.studio.service.InMemoryStudioService;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.*;

class SpringStudioTest {
    @Test
    void applicationServesItsSpringContextAndReleasesTheListeningPort() throws Exception {
        // Given
        String token = "synthetic-editor-token-0123456789";
        int port;
        try (var application = SpringStudioApplication.start(0, token, null, DataCapturePolicy.none())) {
            port = application.port();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/context"))
                    .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(5)).build();
            // When
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            // Then
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("studio-p0.spring", "TEST", "text.trim");
        }
        try (var socket = new Socket()) {
            assertThatThrownBy(() -> socket.connect(new InetSocketAddress("127.0.0.1", port), 1000))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void sameManagementFlowUsesSpringEngineAndPrototypeOperations() {
        // Given
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataCapturePolicy.class, DataCapturePolicy::allowed);
            context.register(StudioSpringConfiguration.class);
            context.refresh();
            var service = context.getBean(InMemoryStudioService.class);
            var resources = context.getBean(ResourceFactory.class);
            assertThat(resources.getResource(PrefixOperation.class))
                    .isNotSameAs(resources.getResource(PrefixOperation.class));
            // When
            var draft = service.create("demo-editor", DemoWiring.sample());
            var report = service.validate("demo-editor", draft.definition());
            var result = service.test("demo-editor", draft.draftId(), 1, UUID.randomUUID(), "  electric  ");
            // Then
            assertThat(report.diagnostics()).isEmpty();
            assertThat(result.state()).isEqualTo(TestSubmission.State.COMPLETED);
            assertThat(result.run().outcome()).isEqualTo("SUCCEEDED");
            assertThat(result.run().output().value()).isEqualTo("ELECTRIC");
            assertThat(result.run().runtimeContext()).isEqualTo("studio-p0.spring");
            assertThat(result.run().revisionId()).isEqualTo(draft.revisionId());
        }
    }
}
