package com.lendflow.kyc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.lendflow.kyc.application.KycResult;
import com.lendflow.kyc.application.KycService;
import com.lendflow.kyc.domain.KycOutcome;
import com.lendflow.shared.error.UpstreamUnavailableException;
import com.lendflow.support.Drafts;
import com.lendflow.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KycIT extends IntegrationTest {

    @Autowired
    KycService kyc;

    @Test
    void aMatchingNamePassesAndIsOnlyCheckedOnce() {
        String pan = Drafts.goodPan();
        UUID id = draft("Rahul Verma", pan);

        KycResult first = kyc.check(id);
        KycResult second = kyc.check(id);

        assertThat(first.outcome()).isEqualTo(KycOutcome.PASS);
        assertThat(second).isEqualTo(first);
        BUREAU.verify(1, postRequestedFor(urlEqualTo("/pan/verify")).withRequestBody(matchingJsonPath("$.pan", equalTo(pan))));
    }

    @Test
    void registryProfilesMapToOutcomes() {
        assertThat(kyc.check(draft("Anita Desai", Drafts.pan(6512))).outcome()).isEqualTo(KycOutcome.REFER);
        assertThat(kyc.check(draft("Priya Sharma", Drafts.pan(6120))).reasons()).containsExactly("KYC_NAME_MISMATCH");
        assertThat(kyc.check(draft("Priya Sharma", Drafts.pan(42))).reasons()).containsExactly("KYC_PAN_INVALID");
    }

    @Test
    void anUnreachableRegistryLeavesNoResultSoTheCheckCanBeRetried() {
        String pan = Drafts.goodPan();
        BUREAU.stubFor(post(urlEqualTo("/pan/verify")).atPriority(1)
            .withRequestBody(matchingJsonPath("$.pan", equalTo(pan)))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        UUID id = draft("Kiran Rao", pan);

        assertThatThrownBy(() -> kyc.check(id)).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(kyc.result(id)).isEmpty();
    }

    private UUID draft(String name, String pan) {
        return UUID.fromString(as("kyc-" + unique(), "lendflow.applicant")
            .post("/v1/applications", Map.of("product", "PERSONAL", "applicant", Drafts.personal(name, pan)))
            .expect(201).body().path("id").asText());
    }

    private static com.github.tomakehurst.wiremock.matching.StringValuePattern equalTo(String value) {
        return com.github.tomakehurst.wiremock.client.WireMock.equalTo(value);
    }
}
