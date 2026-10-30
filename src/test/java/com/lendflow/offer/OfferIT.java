package com.lendflow.offer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.support.Api;
import com.lendflow.support.Drafts;
import com.lendflow.support.Flow;
import com.lendflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class OfferIT extends IntegrationTest {

    private static final String APPLICANT = "lendflow.applicant";

    private record Approved(Api applicant, String applicationId, String offerId) {
    }

    private Approved approved(String name) {
        return approved(as(name.toLowerCase().replace(' ', '-') + "-" + unique(), APPLICANT), name);
    }

    private Approved approved(Api applicant, String name) {
        String id = Flow.submit(applicant, Drafts.personalLoan(name));
        assertThat(Flow.awaitRouted(applicant, id)).isEqualTo("APPROVED");
        String offerId = applicant.get("/v1/applications/" + id + "/offer").expect(200).body().path("id").asText();
        return new Approved(applicant, id, offerId);
    }

    private static Map<String, String> key(String value) {
        return Map.of("Idempotency-Key", value);
    }

    @Test
    void autoApprovalComesWithAPricedOfferAndSchedule() {
        Approved a = approved("Meera Iyer");

        JsonNode offer = a.applicant().get("/v1/offers/" + a.offerId()).expect(200).body();
        assertThat(offer.path("status").asText()).isEqualTo("OFFERED");
        assertThat(offer.path("amount").decimalValue()).isEqualByComparingTo("300000");
        assertThat(offer.path("emi").decimalValue()).isEqualByComparingTo("9750.73");
        assertThat(offer.path("processingFee").decimalValue()).isEqualByComparingTo("3000");
        assertThat(offer.path("feeGst").decimalValue()).isEqualByComparingTo("540");
        assertThat(offer.path("netDisbursal").decimalValue()).isEqualByComparingTo("296460");
        assertThat(offer.path("totalRepayable").decimalValue()).isEqualByComparingTo("351026.39");

        JsonNode schedule = a.applicant().get("/v1/offers/" + a.offerId() + "/schedule").expect(200).body();
        JsonNode rows = schedule.path("instalments");
        assertThat(rows).hasSize(36);
        assertThat(rows.get(0).path("interest").decimalValue()).isEqualByComparingTo("2625.00");
        assertThat(rows.get(35).path("payment").decimalValue()).isEqualByComparingTo("9750.84");
        assertThat(rows.get(35).path("closing").decimalValue()).isEqualByComparingTo("0");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'OfferMade' AND message_key = ?")
            .param(a.applicationId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void acceptingSanctionsTheLoanAndHandsItOff() {
        Approved a = approved("Farhan Ali");

        JsonNode accepted = a.applicant().post("/v1/offers/" + a.offerId() + "/accept", null, key("accept-" + unique()))
            .expect(200).body();

        assertThat(accepted.path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(a.applicant().get("/v1/applications/" + a.applicationId()).expect(200).body().path("status").asText())
            .isEqualTo("SANCTIONED");
        Map<String, Object> event = jdbc.sql("""
                SELECT topic, payload->>'offerId' AS offer, payload->>'netDisbursal' AS net
                FROM outbox WHERE event_type = 'LoanSanctioned' AND message_key = ?
                """).param(a.applicationId()).query().singleRow();
        assertThat(event).containsEntry("topic", "lendflow.sanctions").containsEntry("offer", a.offerId());
        assertThat(new BigDecimal((String) event.get("net"))).isEqualByComparingTo("296460");

        String letter = a.applicant().get("/v1/offers/" + a.offerId() + "/sanction-letter").expect(200).body().asText();
        assertThat(letter).contains("Dear Farhan Ali", "INR 3,00,000.00", "INR 9,750.73", "INR 2,96,460.00");
    }

    @Test
    void parallelRetriesWithOneKeyAcceptOnce() throws Exception {
        Approved a = approved("Ishaan Rao");
        String path = "/v1/offers/" + a.offerId() + "/accept";
        Map<String, String> sameKey = key("accept-" + unique());

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Api.Response>> calls = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    return a.applicant().post(path, null, sameKey);
                }));
            }
            start.countDown();
            List<JsonNode> bodies = new ArrayList<>();
            for (Future<Api.Response> call : calls) {
                bodies.add(call.get().expect(200).body());
            }
            assertThat(bodies).allSatisfy(b -> assertThat(b).isEqualTo(bodies.get(0)));
        }

        assertThat(jdbc.sql("SELECT count(*) FROM application_history WHERE application_id = ?::uuid AND to_status = 'SANCTIONED'")
            .param(a.applicationId()).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'LoanSanctioned' AND message_key = ?")
            .param(a.applicationId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void racingDifferentKeysGiveOneAcceptanceAndConflicts() throws Exception {
        Approved a = approved("Zoya Khan");
        String path = "/v1/offers/" + a.offerId() + "/accept";

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Api.Response>> calls = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            for (int i = 0; i < 6; i++) {
                Map<String, String> own = key("accept-" + unique());
                calls.add(pool.submit(() -> {
                    start.await();
                    return a.applicant().post(path, null, own);
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Api.Response> call : calls) {
                Api.Response response = call.get();
                statuses.add(response.status());
                if (response.status() == 409) {
                    assertThat(response.body().path("code").asText()).isEqualTo("offer-already-accepted");
                }
            }
            assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 409);
        }
    }

    @Test
    void keyReusedForAnotherOfferIsRefused() {
        Api agent = as("agent-" + unique(), APPLICANT);
        Approved first = approved(agent, "Rohan Das");
        Approved second = approved(agent, "Lata Pillai");
        Map<String, String> reused = key("accept-" + unique());
        agent.post("/v1/offers/" + first.offerId() + "/accept", null, reused).expect(200);

        assertThat(agent.post("/v1/offers/" + second.offerId() + "/accept", null, reused).expect(422).body()
            .path("code").asText()).isEqualTo("idempotency-key-reused");
        assertThat(agent.get("/v1/offers/" + second.offerId()).body().path("status").asText()).isEqualTo("OFFERED");
        agent.post("/v1/offers/" + second.offerId() + "/accept", null, Map.of()).expect(400);
    }

    @Test
    void onlyTheApplicantCanAcceptAndOthersCannotSeeTheOffer() {
        Approved a = approved("Anita Shah");

        as("other-" + unique(), APPLICANT).get("/v1/offers/" + a.offerId()).expect(404);
        as("other-" + unique(), APPLICANT).post("/v1/offers/" + a.offerId() + "/accept", null, key("accept-" + unique()))
            .expect(404);
        Api underwriter = as("uw-" + unique(), "lendflow.underwriter");
        underwriter.get("/v1/offers/" + a.offerId()).expect(200);
        underwriter.post("/v1/offers/" + a.offerId() + "/accept", null, key("accept-" + unique())).expect(403);
        assertThat(a.applicant().get("/v1/offers/" + a.offerId() + "/sanction-letter").expect(409).body().path("code")
            .asText()).isEqualTo("offer-not-accepted");
    }

    @Test
    void expiredOfferCannotBeAccepted() {
        Approved a = approved("Kabir Sen");
        jdbc.sql("UPDATE offers SET valid_until = now() - interval '1 minute' WHERE id = ?::uuid").param(a.offerId()).update();

        assertThat(a.applicant().get("/v1/offers/" + a.offerId()).expect(200).body().path("status").asText())
            .isEqualTo("EXPIRED");
        assertThat(a.applicant().post("/v1/offers/" + a.offerId() + "/accept", null, key("accept-" + unique()))
            .expect(409).body().path("code").asText()).isEqualTo("offer-expired");
        assertThat(a.applicant().get("/v1/applications/" + a.applicationId()).expect(200).body().path("status").asText())
            .isEqualTo("APPROVED");
    }
}
