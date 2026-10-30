package com.lendflow.disbursement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.support.Api;
import com.lendflow.support.Drafts;
import com.lendflow.support.Flow;
import com.lendflow.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class DisbursementIT extends IntegrationTest {

    private static final String APPLICANT = "lendflow.applicant";
    private static final String OPS = "lendflow.ops";

    @Autowired
    KafkaTemplate<String, String> kafka;

    private record Sanctioned(Api applicant, String applicationId, String disbursementId) {
    }

    private Sanctioned sanctioned(String name) {
        Api applicant = as("app-" + unique(), APPLICANT);
        String id = Flow.submit(applicant, Drafts.personalLoan(name));
        assertThat(Flow.awaitRouted(applicant, id)).isEqualTo("APPROVED");
        String offerId = applicant.get("/v1/applications/" + id + "/offer").expect(200).body().path("id").asText();
        applicant.post("/v1/offers/" + offerId + "/accept", null, Map.of("Idempotency-Key", "k-" + unique())).expect(200);
        Api ops = as("ops-" + unique(), OPS);
        JsonNode request = await().atMost(Duration.ofSeconds(15)).until(
            () -> ops.get("/v1/disbursements?application=" + id),
            r -> r.status() == 200).body().get(0);
        return new Sanctioned(applicant, id, request.path("id").asText());
    }

    private static Map<String, String> utr(String value) {
        return Map.of("utr", value);
    }

    private static String reference() {
        return ("SBIN" + UUID.randomUUID().toString().replace("-", "")).substring(0, 16).toUpperCase();
    }

    private String status(Sanctioned s) {
        return s.applicant().get("/v1/applications/" + s.applicationId()).expect(200).body().path("status").asText();
    }

    @Test
    void sanctionBecomesAPayoutRequestAndOpsConfirmationDisburses() {
        Sanctioned s = sanctioned("Kavya Reddy");
        Api ops = as("ops-" + unique(), OPS);
        JsonNode request = ops.get("/v1/disbursements/" + s.disbursementId()).expect(200).body();
        assertThat(request.path("status").asText()).isEqualTo("REQUESTED");
        assertThat(request.path("amount").decimalValue()).isEqualByComparingTo("296460");
        assertThat(ops.get("/v1/disbursements").expect(200).body().findValuesAsText("id")).contains(s.disbursementId());

        String ref = reference();
        JsonNode sent = ops.post("/v1/disbursements/" + s.disbursementId() + "/confirm", utr(ref.toLowerCase()))
            .expect(200).body();
        assertThat(sent.path("status").asText()).isEqualTo("SENT");
        assertThat(sent.path("utr").asText()).isEqualTo(ref);
        assertThat(status(s)).isEqualTo("DISBURSED");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'LoanDisbursed' AND message_key = ?")
            .param(s.applicationId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void confirmationIsIdempotentOnTheUtrAndRefusesAnother() {
        Sanctioned s = sanctioned("Rohit Verma");
        Api ops = as("ops-" + unique(), OPS);
        String path = "/v1/disbursements/" + s.disbursementId() + "/confirm";
        String ref = reference();

        assertThat(ops.post(path, utr("SBIN-12")).expect(400).body().path("code").asText()).isEqualTo("utr-invalid");
        ops.post(path, utr(ref)).expect(200);
        ops.post(path, utr(ref)).expect(200);
        assertThat(ops.post(path, utr(reference())).expect(409).body().path("code").asText())
            .isEqualTo("already-disbursed");
        assertThat(jdbc.sql("SELECT count(*) FROM application_history WHERE application_id = ?::uuid AND to_status = 'DISBURSED'")
            .param(s.applicationId()).query(Integer.class).single()).isEqualTo(1);

        Sanctioned other = sanctioned("Neha Kapoor");
        assertThat(ops.post("/v1/disbursements/" + other.disbursementId() + "/confirm", utr(ref)).expect(409).body()
            .path("code").asText()).isEqualTo("utr-already-used");
        assertThat(status(other)).isEqualTo("SANCTIONED");
    }

    @Test
    void redeliveredAndRepublishedSanctionsCreateNoSecondPayout() throws Exception {
        Sanctioned s = sanctioned("Arjun Malhotra");
        Map<String, Object> row = jdbc.sql("""
                SELECT event_id::text AS id, payload::text AS payload FROM outbox
                WHERE event_type = 'LoanSanctioned' AND message_key = ?
                """).param(s.applicationId()).query().singleRow();
        String payload = (String) row.get("payload");
        String redelivered = envelope((String) row.get("id"), payload);
        String republishedId = UUID.randomUUID().toString();
        String republished = envelope(republishedId, payload);

        kafka.send("lendflow.sanctions", s.applicationId(), redelivered).get();
        kafka.send("lendflow.sanctions", s.applicationId(), republished).get();

        // Same key, same partition: once the second is processed, the first has been too.
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.sql(
                "SELECT count(*) FROM processed_events WHERE consumer = 'disbursement' AND event_id = ?::uuid")
            .param(republishedId).query(Integer.class).single() == 1);
        assertThat(jdbc.sql("SELECT count(*) FROM disbursements WHERE application_id = ?::uuid")
            .param(s.applicationId()).query(Integer.class).single()).isEqualTo(1);
    }

    private static String envelope(String eventId, String payload) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"LoanSanctioned\",\"occurredAt\":\"2026-10-30T06:00:00Z\","
            + "\"payload\":" + payload + "}";
    }

    @Test
    void onlyOpsSeesOrMovesPayouts() {
        Sanctioned s = sanctioned("Divya Pillai");
        s.applicant().get("/v1/disbursements/" + s.disbursementId()).expect(403);
        as("uw-" + unique(), "lendflow.underwriter", "lendflow.credit_head")
            .post("/v1/disbursements/" + s.disbursementId() + "/confirm", utr(reference())).expect(403);
        as("ops-" + unique(), OPS).post("/v1/disbursements/" + UUID.randomUUID() + "/confirm", utr(reference()))
            .expect(404);
    }
}
