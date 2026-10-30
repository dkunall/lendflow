package com.lendflow.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.support.Api;
import com.lendflow.support.Drafts;
import com.lendflow.support.Flow;
import com.lendflow.support.IntegrationTest;
import com.lendflow.workflow.application.Assessment;
import com.lendflow.workflow.application.AssessmentJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkflowIT extends IntegrationTest {

    private static final String APPLICANT = "lendflow.applicant";

    @Autowired
    Assessment assessment;

    @Test
    void cleanFileIsApprovedWithItsWorkingRecorded() {
        Api ravi = as("ravi-" + unique(), APPLICANT);
        String id = Flow.submit(ravi, Drafts.personalLoan("Ravi Menon"));

        assertThat(Flow.awaitRouted(ravi, id)).as(() -> history(id)).isEqualTo("APPROVED");
        Api underwriter = as("uw-" + unique(), "lendflow.underwriter");
        JsonNode decision = underwriter.get("/v1/applications/" + id + "/decision").expect(200).body();
        assertThat(decision.path("ruleset").asText()).isEqualTo("PERSONAL-v1");
        assertThat(decision.path("reasons").toString()).isEqualTo("[\"AUTO_APPROVED\"]");
        assertThat(decision.path("score").asInt()).isEqualTo(670);
        assertThat(decision.path("approvedAmount").decimalValue()).isEqualByComparingTo("300000");
        assertThat(decision.path("emi").decimalValue()).isEqualByComparingTo("9750.73");
        assertThat(decision.path("inputs").path("bureauScore").asInt()).isEqualTo(782);
        assertThat(decision.path("firedRules").path("decision").toString()).isEqualTo("[\"D9\"]");

        List<String> path = jdbc.sql("SELECT to_status FROM application_history WHERE application_id = ?::uuid ORDER BY id")
            .param(id).query(String.class).list();
        assertThat(path).endsWith("SUBMITTED", "UNDERWRITING", "APPROVED");
        assertThat(jdbc.sql("SELECT challenger_id FROM shadow_scores WHERE application_id = ?::uuid").param(id)
            .query(String.class).single()).isEqualTo("PL-SC-2");
        assertThatThrownBy(() -> jdbc.sql("UPDATE decisions SET outcome = 'REJECT' WHERE application_id = ?::uuid").param(id).update())
            .hasMessageContaining("immutable");
    }

    private String history(String id) {
        return jdbc.sql("SELECT string_agg(to_status || ':' || coalesce(reason, ''), ' > ' ORDER BY id) FROM application_history WHERE application_id = ?::uuid")
            .param(id).query(String.class).single()
            + " job=" + jdbc.sql("SELECT attempts || ' ' || coalesce(last_error, '') FROM workflow_jobs WHERE application_id = ?::uuid")
            .param(id).query(String.class).single();
    }

    @Test
    void applicantSeesReasonsButNotTheScoring() {
        Api usha = as("usha-" + unique(), APPLICANT);
        String id = Flow.submit(usha, Drafts.personalLoan(Drafts.personal("Usha Rao", Drafts.pan(8120)), "300000", 36,
            "90000", "5000"));

        assertThat(Flow.awaitRouted(usha, id)).isEqualTo("REJECTED");
        JsonNode mine = usha.get("/v1/applications/" + id + "/decision").expect(200).body();
        assertThat(mine.path("reasons").toString()).contains("ELIG_WRITTEN_OFF_ACCOUNT", "ELIG_SERIOUS_DELINQUENCY");
        assertThat(mine.has("score")).isFalse();
        assertThat(mine.has("approvedAmount")).isFalse();
        assertThat(mine.has("inputs")).isFalse();
        as("someone-" + unique(), APPLICANT).get("/v1/applications/" + id + "/decision").expect(404);
    }

    @Test
    void noBureauHistoryIsReferred() {
        Api kiran = as("kiran-" + unique(), APPLICANT);
        String id = Flow.submit(kiran, Drafts.personalLoan(Drafts.personal("Kiran Joshi", Drafts.pan(9050)), "300000", 36,
            "90000", "0"));

        assertThat(Flow.awaitRouted(kiran, id)).isEqualTo("REFER");
        assertThat(kiran.get("/v1/applications/" + id + "/decision").expect(200).body().path("reasons").toString())
            .startsWith("[\"BUREAU_NO_HIT\",\"SCORE_BORDERLINE\"");
    }

    @Test
    void bureauOutageIsRetriedThenReferredWithoutADecision() {
        Api dev = as("dev-" + unique(), APPLICANT);
        String id = Flow.submit(dev, Drafts.personalLoan(Drafts.personal("Dev Anand", Drafts.pan(3300)), "300000", 36,
            "90000", "5000"));

        assertThat(Flow.awaitRouted(dev, id)).isEqualTo("REFER");
        Map<String, Object> job = jdbc.sql("SELECT status, attempts, last_error FROM workflow_jobs WHERE application_id = ?::uuid")
            .param(id).query().singleRow();
        assertThat(job).containsEntry("status", "DONE").containsEntry("attempts", 3)
            .containsEntry("last_error", "BUREAU_UNAVAILABLE");
        assertThat(jdbc.sql("SELECT reason FROM application_history WHERE application_id = ?::uuid AND to_status = 'REFER'")
            .param(id).query(String.class).single()).isEqualTo("BUREAU_UNAVAILABLE");
        dev.get("/v1/applications/" + id + "/decision").expect(404);
    }

    @Test
    void secondApplicationFromTheSamePersonIsReferredAsDuplicate() {
        Api neha = as("neha-" + unique(), APPLICANT);
        Map<String, Object> person = Drafts.personal("Neha Kapoor", Drafts.goodPan());
        String first = Flow.submit(neha, Drafts.personalLoan(person, "300000", 36, "90000", "5000"));
        assertThat(Flow.awaitRouted(neha, first)).isEqualTo("APPROVED");

        String second = Flow.submit(neha, Drafts.personalLoan(person, "200000", 24, "90000", "5000"));

        assertThat(Flow.awaitRouted(neha, second)).isEqualTo("REFER");
        assertThat(neha.get("/v1/applications/" + second + "/decision").expect(200).body().path("reasons").toString())
            .isEqualTo("[\"DUPLICATE_SUSPECTED\"]");
    }

    @Test
    void racingWorkersOnOneJobRouteItOnce() throws Exception {
        Api tara = as("tara-" + unique(), APPLICANT);
        String id = Flow.submit(tara, Drafts.personalLoan("Tara Bhat"));
        UUID jobId = jdbc.sql("SELECT id FROM workflow_jobs WHERE application_id = ?::uuid").param(id).query(UUID.class).single();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Assessment.Result>> runs = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            for (int i = 0; i < 6; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return assessment.run(new AssessmentJob(jobId, UUID.fromString(id), 1));
                }));
            }
            start.countDown();
            for (Future<Assessment.Result> run : runs) {
                assertThat(run.get()).isNotEqualTo(Assessment.Result.FAILED);
            }
        }

        assertThat(Flow.awaitRouted(tara, id)).isEqualTo("APPROVED");
        assertThat(jdbc.sql("SELECT count(*) FROM application_history WHERE application_id = ?::uuid AND to_status IN ('UNDERWRITING', 'APPROVED')")
            .param(id).query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM decisions WHERE application_id = ?::uuid").param(id)
            .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'CreditDecided' AND message_key = ?").param(id)
            .query(Integer.class).single()).isEqualTo(1);
    }
}
