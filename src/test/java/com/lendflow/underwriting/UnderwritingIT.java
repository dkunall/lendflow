package com.lendflow.underwriting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.support.Api;
import com.lendflow.support.Drafts;
import com.lendflow.support.Flow;
import com.lendflow.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UnderwritingIT extends IntegrationTest {

    private static final String APPLICANT = "lendflow.applicant";
    private static final String UNDERWRITER = "lendflow.underwriter";
    private static final String MANAGER = "lendflow.credit_manager";
    private static final String HEAD = "lendflow.credit_head";

    private record Referred(Api applicant, String applicationId, String taskId) {
    }

    private Referred referred(String name, int panDigits, String amount, int tenure, String income) {
        Api applicant = as("app-" + unique(), APPLICANT);
        String id = Flow.submit(applicant, Drafts.personalLoan(Drafts.personal(name, Drafts.pan(panDigits)), amount,
            tenure, income, "0"));
        assertThat(Flow.awaitRouted(applicant, id)).isEqualTo("REFER");
        return new Referred(applicant, id, activeTask(id).path("id").asText());
    }

    private Referred noHit(String name) {
        return referred(name, 9050, "300000", 36, "90000");
    }

    private JsonNode activeTask(String applicationId) {
        JsonNode tasks = as("uw-" + unique(), UNDERWRITER).get("/v1/tasks?application=" + applicationId).expect(200)
            .body();
        JsonNode last = tasks.get(tasks.size() - 1);
        assertThat(last.path("status").asText()).isNotEqualTo("DONE");
        return last;
    }

    private static Map<String, String> completion(String action, String note) {
        return Map.of("action", action, "note", note);
    }

    private String status(Referred r) {
        return r.applicant().get("/v1/applications/" + r.applicationId()).expect(200).body().path("status").asText();
    }

    @Test
    void makerRecommendsAndADifferentCheckerConfirms() {
        Referred r = noHit("Gita Nair");
        Api maker = as("maker-" + unique(), UNDERWRITER, MANAGER);
        maker.post("/v1/tasks/" + r.taskId() + "/claim", null).expect(200);
        JsonNode done = maker.post("/v1/tasks/" + r.taskId() + "/complete",
            completion("APPROVE", "Salary credits verified from statements")).expect(200).body();
        assertThat(done.path("status").asText()).isEqualTo("DONE");

        JsonNode check = activeTask(r.applicationId());
        assertThat(check.path("stage").asText()).isEqualTo("CHECKER");
        assertThat(check.path("role").asText()).isEqualTo("CREDIT_MANAGER");
        assertThat(check.path("amount").decimalValue()).isEqualByComparingTo("300000");
        String checkId = check.path("id").asText();

        assertThat(maker.post("/v1/tasks/" + checkId + "/claim", null).expect(403).body().path("code").asText())
            .isEqualTo("four-eyes");
        assertThat(as("uw-" + unique(), UNDERWRITER).post("/v1/tasks/" + checkId + "/claim", null).expect(403).body()
            .path("code").asText()).isEqualTo("insufficient-authority");
        assertRefused("UPDATE underwriting_tasks SET status = 'CLAIMED', assignee = maker WHERE id = '" + checkId + "'::uuid");
        assertThat(status(r)).isEqualTo("REFER");

        Api checker = as("checker-" + unique(), MANAGER);
        checker.post("/v1/tasks/" + checkId + "/claim", null).expect(200);
        checker.post("/v1/tasks/" + checkId + "/complete", completion("CONFIRM", "Agree")).expect(200);

        assertThat(status(r)).isEqualTo("APPROVED");
        r.applicant().get("/v1/applications/" + r.applicationId() + "/offer").expect(200);
        assertThat(checker.post("/v1/tasks/" + checkId + "/complete", completion("CONFIRM", "Again")).expect(409)
            .body().path("code").asText()).isEqualTo("task-not-held");
    }

    private void assertRefused(String update) {
        try {
            jdbc.sql(update).update();
            throw new AssertionError("expected the database to refuse: " + update);
        } catch (DataIntegrityViolationException expected) {
            assertThat(expected.getMessage()).contains("underwriting_tasks_check");
        }
    }

    @Test
    void onlyOneOfManyUnderwritersGetsTheClaim() throws Exception {
        Referred r = noHit("Hari Prasad");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> claims = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                Api underwriter = as("uw-" + unique() + "-" + i, UNDERWRITER);
                claims.add(pool.submit(() -> {
                    start.await();
                    return underwriter.post("/v1/tasks/" + r.taskId() + "/claim", null).status();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> claim : claims) {
                statuses.add(claim.get());
            }
            assertThat(statuses).containsOnlyOnce(200).containsOnly(200, 409);
        }
    }

    @Test
    void largeAmountNeedsTheCreditHead() {
        Referred r = referred("Vikram Seth", 1200, "2000000", 36, "200000");
        JsonNode decision = as("uw-" + unique(), UNDERWRITER).get("/v1/applications/" + r.applicationId() + "/decision")
            .expect(200).body();
        assertThat(decision.path("reasons").toString()).contains("ABOVE_AUTO_APPROVAL_LIMIT");
        assertThat(decision.path("approvedAmount").decimalValue()).isGreaterThan(new BigDecimal("1500000"));

        Api maker = as("maker-" + unique(), UNDERWRITER);
        maker.post("/v1/tasks/" + r.taskId() + "/claim", null).expect(200);
        maker.post("/v1/tasks/" + r.taskId() + "/complete", completion("APPROVE", "Strong file")).expect(200);
        String checkId = activeTask(r.applicationId()).path("id").asText();
        assertThat(activeTask(r.applicationId()).path("role").asText()).isEqualTo("CREDIT_HEAD");

        as("cm-" + unique(), MANAGER).post("/v1/tasks/" + checkId + "/claim", null).expect(403);
        Api head = as("head-" + unique(), HEAD);
        head.post("/v1/tasks/" + checkId + "/claim", null).expect(200);
        head.post("/v1/tasks/" + checkId + "/complete", completion("CONFIRM", "Approved at head level")).expect(200);
        assertThat(status(r)).isEqualTo("APPROVED");
    }

    @Test
    void withoutPricedTermsTheOnlyWayOutIsADecline() {
        Referred r = referred("Om Prakash", 3300, "300000", 36, "90000");
        Api maker = as("maker-" + unique(), UNDERWRITER);
        maker.post("/v1/tasks/" + r.taskId() + "/claim", null).expect(200);
        assertThat(maker.post("/v1/tasks/" + r.taskId() + "/complete", completion("APPROVE", "Looks fine")).expect(422)
            .body().path("code").asText()).isEqualTo("no-approved-terms");
        maker.post("/v1/tasks/" + r.taskId() + "/complete", completion("DECLINE", "Cannot verify credit history"))
            .expect(200);

        String checkId = activeTask(r.applicationId()).path("id").asText();
        Api checker = as("cm-" + unique(), MANAGER);
        checker.post("/v1/tasks/" + checkId + "/claim", null).expect(200);
        checker.post("/v1/tasks/" + checkId + "/complete", completion("CONFIRM", "Agree")).expect(200);
        assertThat(status(r)).isEqualTo("REJECTED");
    }

    @Test
    void sentBackWorkReturnsToTheMakerQueue() {
        Referred r = noHit("Leela Menon");
        Api maker = as("maker-" + unique(), UNDERWRITER);
        maker.post("/v1/tasks/" + r.taskId() + "/claim", null).expect(200);
        maker.post("/v1/tasks/" + r.taskId() + "/complete", completion("APPROVE", "OK")).expect(200);
        String checkId = activeTask(r.applicationId()).path("id").asText();
        Api checker = as("cm-" + unique(), MANAGER);
        checker.post("/v1/tasks/" + checkId + "/claim", null).expect(200);
        checker.post("/v1/tasks/" + checkId + "/complete", completion("SEND_BACK", "Need employer confirmation"))
            .expect(200);

        JsonNode again = activeTask(r.applicationId());
        assertThat(again.path("stage").asText()).isEqualTo("MAKER");
        assertThat(again.path("status").asText()).isEqualTo("OPEN");
        assertThat(status(r)).isEqualTo("REFER");
    }

    @Test
    void overdueTasksAreReleasedAndEscalated() {
        Referred r = noHit("Sunil Gavaskar");
        Api maker = as("maker-" + unique(), UNDERWRITER);
        maker.post("/v1/tasks/" + r.taskId() + "/claim", null).expect(200);
        jdbc.sql("UPDATE underwriting_tasks SET due_at = now() - interval '1 minute' WHERE id = ?::uuid")
            .param(r.taskId()).update();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(activeTask(r.applicationId()).path("escalations").asInt()).isEqualTo(1));
        JsonNode task = activeTask(r.applicationId());
        assertThat(task.path("role").asText()).isEqualTo("CREDIT_MANAGER");
        assertThat(task.path("status").asText()).isEqualTo("OPEN");
        assertThat(task.path("assignee").isNull()).isTrue();
        assertThat(maker.post("/v1/tasks/" + r.taskId() + "/complete", completion("APPROVE", "Late")).expect(409)
            .body().path("code").asText()).isEqualTo("task-not-held");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'TaskEscalated' AND message_key = ?")
            .param(r.applicationId()).query(Integer.class).single()).isEqualTo(1);
    }
}
