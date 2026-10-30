package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lendflow.decision.application.PolicyCatalog;
import com.lendflow.shared.domain.Product;
import com.lendflow.support.Api;
import com.lendflow.support.Drafts;
import com.lendflow.support.Flow;
import com.lendflow.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Publishing changes the policy for every test sharing these containers, so the publish test uses an unchanged
 * copy of the home policy; the stricter personal policy is only ever simulated.
 */
class RulesetIT extends IntegrationTest {

    private static final String POLICY = "lendflow.policy";

    @Autowired
    PolicyCatalog catalog;

    private JsonNode published(Api admin, String product) {
        for (JsonNode r : admin.get("/v1/rulesets?product=" + product).expect(200).body()) {
            if (r.path("status").asText().equals("PUBLISHED")) {
                return r;
            }
        }
        throw new AssertionError("No published " + product + " ruleset");
    }

    @Test
    void simulationShowsWhichPastDecisionsAStricterDraftWouldChange() {
        Api applicant = as("app-" + unique(), "lendflow.applicant");
        String approved = Flow.submit(applicant, Drafts.personalLoan("Tanvi Joshi"));
        assertThat(Flow.awaitRouted(applicant, approved)).isEqualTo("APPROVED");

        Api author = as("policy-" + unique(), POLICY);
        ObjectNode definition = published(author, "PERSONAL").path("definition").deepCopy();
        ((ObjectNode) definition.get("parameters")).put("autoApproveLimit", "1000");
        JsonNode draft = author.post("/v1/rulesets", Map.of("product", "PERSONAL", "definition", definition))
            .expect(201).body();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("version").asInt()).isGreaterThan(1);

        JsonNode result = author.post("/v1/rulesets/" + draft.path("id").asText() + "/simulate?limit=50", null)
            .expect(200).body();
        assertThat(result.path("comparedWith").asText()).isEqualTo(catalog.published(Product.PERSONAL).stored().key());
        JsonNode report = result.path("report");
        assertThat(report.path("evaluated").asInt()).isBetween(1, 50);
        assertThat(report.path("matrix").path("APPROVE").has("APPROVE")).isFalse();
        JsonNode first = report.path("sample").get(0);
        assertThat(first.path("applicationId").asText()).isEqualTo(approved);
        assertThat(first.path("to").asText()).isEqualTo("REFER");
        assertThat(first.path("reasons").toString()).contains("ABOVE_AUTO_APPROVAL_LIMIT");
        assertThat(published(author, "PERSONAL").path("version").asInt()).isEqualTo(1);
    }

    @Test
    void anotherPersonPublishesAndThePreviousVersionRetires() {
        Api author = as("policy-" + unique(), POLICY);
        JsonNode before = published(author, "HOME");
        JsonNode draft = author.post("/v1/rulesets", Map.of("product", "HOME", "definition", before.path("definition")))
            .expect(201).body();
        String path = "/v1/rulesets/" + draft.path("id").asText() + "/publish";

        assertThat(author.post(path, null).expect(403).body().path("code").asText()).isEqualTo("four-eyes");
        JsonNode published = as("reviewer-" + unique(), POLICY).post(path, null).expect(200).body();

        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(author.get("/v1/rulesets/" + before.path("id").asText()).expect(200).body().path("status").asText())
            .isEqualTo("RETIRED");
        assertThat(catalog.published(Product.HOME).stored().version()).isEqualTo(draft.path("version").asInt());
        assertThat(as("other-" + unique(), POLICY).post(path, null).expect(409).body().path("code").asText())
            .isEqualTo("ruleset-not-draft");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = 'RulesetPublished' AND payload->>'rulesetId' = ?")
            .param(draft.path("id").asText()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void draftsAreValidatedAndOnlyPolicyAdminsTouchThem() {
        Api author = as("policy-" + unique(), POLICY);
        ObjectNode broken = published(author, "PERSONAL").path("definition").deepCopy();
        broken.put("champion", "NO-SUCH-CARD");
        assertThat(author.post("/v1/rulesets", Map.of("product", "PERSONAL", "definition", broken)).expect(400).body()
            .path("code").asText()).isEqualTo("ruleset-invalid");
        author.post("/v1/rulesets", Map.of("product", "PERSONAL")).expect(400);

        as("cm-" + unique(), "lendflow.credit_head").get("/v1/rulesets?product=PERSONAL").expect(403);
        as("app-" + unique(), "lendflow.applicant")
            .post("/v1/rulesets", Map.of("product", "PERSONAL", "definition", broken)).expect(403);
    }
}
