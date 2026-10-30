package com.lendflow.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class SecurityIT extends IntegrationTest {

    @Test
    void healthIsPublicAndUp() {
        assertThat(anonymous.get("/actuator/health/readiness").expect(200).body().path("status").asText()).isEqualTo("UP");
    }

    @Test
    void apiRequiresAToken() {
        assertThat(anonymous.get("/v1/applications").expect(401).body().path("code").asText()).isEqualTo("unauthenticated");
    }

    @Test
    void staffEndpointsRejectApplicants() {
        assertThat(as("someone-" + unique(), "lendflow.applicant").get("/v1/tasks").expect(403).body().path("code").asText())
            .isEqualTo("forbidden");
    }
}
