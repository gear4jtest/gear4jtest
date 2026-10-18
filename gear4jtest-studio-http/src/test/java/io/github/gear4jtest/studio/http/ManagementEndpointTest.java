package io.github.gear4jtest.studio.http;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ManagementEndpointTest {
    @Test
    void everyRouteHasUniqueDispatchAndExplicitFederationClassification() {
        // Given / When / Then: the handler has an exhaustive switch over this enum.
        var keys = new HashSet<String>();
        for (var endpoint : ManagementEndpoint.values()) {
            String path = endpoint.template().replace("{id}", "00000000-0000-0000-0000-000000000001");
            assertThat(keys.add(endpoint.method() + path)).isTrue();
            assertThat(ManagementEndpoint.match(endpoint.method(), path)).contains(endpoint);
            assertThat(endpoint.exposure()).isNotNull();
            assertThat(endpoint.federated()).isEqualTo(endpoint != ManagementEndpoint.RUN_EVENTS);
        }
        assertThat(ManagementEndpoint.match("GET", "/api/v1/drafts/1-1-1-1-1")).isEmpty();
        assertThat(ManagementEndpoint.match("POST", "/api/v1/admin")).isEmpty();
        assertThat(ManagementEndpoint.match("GET", "/api/v1/catalog/../context")).isEmpty();
    }
}
