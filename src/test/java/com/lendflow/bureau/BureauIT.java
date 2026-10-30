package com.lendflow.bureau;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.bureau.application.BureauService;
import com.lendflow.bureau.domain.CreditReport;
import com.lendflow.shared.error.DomainException;
import com.lendflow.support.Drafts;
import com.lendflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BureauIT extends IntegrationTest {

    @Autowired
    BureauService bureau;

    @Test
    void aReportIsPulledOnceWithConsentAndStored() {
        String pan = Drafts.goodPan();
        UUID id = draft(pan, true);

        CreditReport first = bureau.pull(id);
        CreditReport again = bureau.pull(id);

        assertThat(first.hit()).isTrue();
        assertThat(first.score()).isEqualTo(782);
        assertThat(first.reportedEmi()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(first.bureauRef()).startsWith("CIR-");
        assertThat(again).isEqualTo(first);
        BUREAU.verify(1, postRequestedFor(urlEqualTo("/bureau/v1/credit-report"))
            .withRequestBody(matchingJsonPath("$.pan", equalTo(pan)))
            .withRequestBody(matchingJsonPath("$.consent.version", equalTo("bureau-consent-2026-01"))));
    }

    @Test
    void noHitIsAReportWithoutAScore() {
        CreditReport report = bureau.pull(draft(Drafts.pan(9301), true));

        assertThat(report.hit()).isFalse();
        assertThat(report.score()).isNull();
    }

    @Test
    void withoutConsentTheBureauIsNeverCalled() {
        String pan = Drafts.goodPan();
        UUID id = draft(pan, false);

        assertThatThrownBy(() -> bureau.pull(id)).isInstanceOfSatisfying(DomainException.class,
            e -> assertThat(e.code()).isEqualTo("consent-missing"));
        BUREAU.verify(0, postRequestedFor(urlEqualTo("/bureau/v1/credit-report"))
            .withRequestBody(matchingJsonPath("$.pan", equalTo(pan))));
    }

    @Test
    void aFailingBureauIsRetriedAndLeavesNothingStored() {
        String pan = Drafts.pan(3400);
        UUID id = draft(pan, true);

        assertThatThrownBy(() -> bureau.pull(id)).isInstanceOfSatisfying(DomainException.class,
            e -> assertThat(e.code()).isEqualTo("bureau-unavailable"));
        BUREAU.verify(3, postRequestedFor(urlEqualTo("/bureau/v1/credit-report"))
            .withRequestBody(matchingJsonPath("$.pan", equalTo(pan))));
        assertThat(bureau.report(id)).isEmpty();
    }

    @Test
    void aBureauSlowerThanTheTimeoutCountsAsAFailure() {
        UUID id = draft(Drafts.pan(7100), true);
        long started = System.nanoTime();

        assertThatThrownBy(() -> bureau.pull(id)).isInstanceOfSatisfying(DomainException.class,
            e -> assertThat(e.code()).isEqualTo("bureau-unavailable"));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(4000 * 3);
    }

    private UUID draft(String pan, boolean consent) {
        Map<String, Object> body = new HashMap<>(Drafts.personalLoan(Drafts.personal("Meera Pillai", pan), "300000", 36,
            "90000", "5000"));
        if (!consent) {
            body.remove("consent");
        }
        return UUID.fromString(as("bureau-" + unique(), "lendflow.applicant").post("/v1/applications", body)
            .expect(201).body().path("id").asText());
    }
}
