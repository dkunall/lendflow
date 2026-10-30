package com.lendflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.support.Api;
import com.lendflow.support.Documents;
import com.lendflow.support.Drafts;
import com.lendflow.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ApplicationIT extends IntegrationTest {

    private static final String APPLICANT = "lendflow.applicant";

    @Test
    void aDraftShowsMaskedIdentifiersAndAVersionEtag() {
        Api rahul = as("rahul-" + unique(), APPLICANT);
        String pan = Drafts.goodPan();

        Api.Response created = rahul.post("/v1/applications",
            Map.of("product", "PERSONAL", "amount", "250000", "applicant", Map.of("fullName", "Rahul Verma", "pan", pan)))
            .expect(201);

        assertThat(created.headers().getETag()).isEqualTo("\"1\"");
        assertThat(created.body().path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.body().path("terms").path("amount").decimalValue()).isEqualByComparingTo("250000");
        assertThat(created.body().path("applicant").path("panMasked").asText()).isEqualTo("XXXXX" + pan.substring(5));
        assertThat(created.body().toString()).doesNotContain(pan);
    }

    @Test
    void identifiersAreNotStoredInTheClear() {
        Api priya = as("priya-" + unique(), APPLICANT);
        Map<String, Object> body = Drafts.personalLoan("Priya Sharma");
        @SuppressWarnings("unchecked")
        Map<String, Object> applicant = (Map<String, Object>) body.get("applicant");
        String id = priya.post("/v1/applications", body).expect(201).body().path("id").asText();

        String row = jdbc.sql("SELECT a::text FROM applicants a WHERE application_id = ?::uuid").param(id)
            .query(String.class).single();

        assertThat(row).doesNotContain((String) applicant.get("pan"))
            .doesNotContain((String) applicant.get("phone"))
            .doesNotContain((String) applicant.get("email"));
    }

    @Test
    void updatesNeedTheCurrentVersion() {
        Api arjun = as("arjun-" + unique(), APPLICANT);
        String id = arjun.post("/v1/applications", Map.of("product", "PERSONAL")).expect(201).body().path("id").asText();
        String path = "/v1/applications/" + id;

        assertThat(arjun.patch(path, Map.of("amount", "100000")).expect(400).body().path("code").asText())
            .isEqualTo("if-match-required");
        Api.Response updated = arjun.patch(path, Map.of("amount", "100000"), Map.of("If-Match", "\"1\"")).expect(200);
        assertThat(updated.headers().getETag()).isEqualTo("\"2\"");
        assertThat(arjun.patch(path, Map.of("tenureMonths", 24), Map.of("If-Match", "\"1\"")).expect(412)
            .body().path("code").asText()).isEqualTo("version-mismatch");
        assertThat(arjun.patch(path, Map.of("product", "HOME"), Map.of("If-Match", "\"2\"")).expect(422)
            .body().path("code").asText()).isEqualTo("product-immutable");
    }

    @Test
    void concurrentEditsFromTheSameVersionLetExactlyOneWin() throws Exception {
        Api meera = as("meera-" + unique(), APPLICANT);
        String id = meera.post("/v1/applications", Map.of("product", "PERSONAL")).expect(201).body().path("id").asText();

        List<Integer> statuses = race(8, i -> meera.patch("/v1/applications/" + id,
            Map.of("amount", String.valueOf(100000 + i * 1000)), Map.of("If-Match", "\"1\"")).status());

        assertThat(statuses).containsOnly(200, 412);
        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(meera.get("/v1/applications/" + id).expect(200).body().path("version").asInt()).isEqualTo(2);
    }

    @Test
    void otherApplicantsCannotSeeADraftButStaffCan() {
        Api owner = as("owner-" + unique(), APPLICANT);
        String id = owner.post("/v1/applications", Map.of("product", "HOME")).expect(201).body().path("id").asText();

        as("stranger-" + unique(), APPLICANT).get("/v1/applications/" + id).expect(404);
        as("uw-" + unique(), "lendflow.underwriter").get("/v1/applications/" + id).expect(200);
    }

    @Test
    void submittingAnIncompleteDraftListsWhatIsMissing() {
        Api kavya = as("kavya-" + unique(), APPLICANT);
        String id = kavya.post("/v1/applications", Map.of("product", "HOME", "amount", "4000000")).expect(201)
            .body().path("id").asText();

        Api.Response rejected = kavya.post("/v1/applications/" + id + "/submit", null).expect(422);

        assertThat(rejected.body().path("code").asText()).isEqualTo("application-incomplete");
        List<String> missing = new ArrayList<>();
        rejected.body().path("missing").forEach(n -> missing.add(n.asText()));
        assertThat(missing).contains("tenureMonths", "propertyValue", "applicant", "bureauConsent",
            "document:PAN_CARD", "document:PROPERTY_DOCUMENT");
    }

    @Test
    void concurrentSubmitsAllSucceedButTheApplicationMovesOnce() throws Exception {
        Api vikram = as("vikram-" + unique(), APPLICANT);
        String id = vikram.post("/v1/applications", Drafts.personalLoan("Vikram Rao")).expect(201)
            .body().path("id").asText();
        Documents.uploadRequired(vikram, id, false);

        List<Integer> statuses = race(8, i -> vikram.post("/v1/applications/" + id + "/submit", null).status());

        assertThat(statuses).containsOnly(200);
        int submits = jdbc.sql("SELECT count(*) FROM application_history WHERE application_id = ?::uuid AND event = 'SUBMIT'")
            .param(id).query(Integer.class).single();
        int events = jdbc.sql("SELECT count(*) FROM outbox WHERE message_key = ? AND payload->>'event' = 'SUBMIT'")
            .param(id).query(Integer.class).single();
        assertThat(submits).isEqualTo(1);
        assertThat(events).isEqualTo(1);
        assertThat(vikram.get("/v1/applications/" + id).expect(200).body().path("submittedAt").isNull()).isFalse();
    }

    @Test
    void aSubmittedApplicationCanNoLongerBeEditedOrWithdrawn() {
        Api sana = as("sana-" + unique(), APPLICANT);
        // A bureau that is down keeps the application in checks, then refers it; neither state can be withdrawn.
        Map<String, Object> draft = Drafts.personalLoan(Drafts.personal("Sana Qureshi", Drafts.pan(3150)), "300000", 36,
            "90000", "5000");
        String id = sana.post("/v1/applications", draft).expect(201).body().path("id").asText();
        Documents.uploadRequired(sana, id, false);
        int version = sana.post("/v1/applications/" + id + "/submit", null).expect(200).body().path("version").asInt();

        assertThat(sana.patch("/v1/applications/" + id, Map.of("amount", "1000"), Map.of("If-Match", "\"" + version + "\""))
            .status()).isIn(409, 412);
        assertThat(sana.post("/v1/applications/" + id + "/withdraw", null).expect(409).body().path("code").asText())
            .isEqualTo("illegal-transition");
    }

    private static List<Integer> race(int threads, IndexedCall call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Integer> task = () -> {
                    start.await();
                    return call.run(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            return statuses;
        }
    }

    @FunctionalInterface
    interface IndexedCall {
        int run(int index);
    }
}
