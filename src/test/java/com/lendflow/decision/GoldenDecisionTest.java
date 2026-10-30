package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lendflow.decision.application.PolicyFormat;
import com.lendflow.decision.domain.CreditDecision;
import com.lendflow.decision.domain.CreditFacts;
import com.lendflow.decision.domain.DecisionEngine;
import com.lendflow.decision.domain.Ruleset;
import com.lendflow.decision.domain.Scorecard;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Decisions for hand-picked applicants against the seeded policies. Amounts and EMIs in the files were
 * worked out separately (Python decimal, linear search for the largest amount), not copied from this code.
 * A policy edit that changes any of them should fail here and be reviewed.
 */
class GoldenDecisionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> personal() throws IOException {
        return cases("golden/personal.json");
    }

    @TestFactory
    Stream<DynamicTest> home() throws IOException {
        return cases("golden/home.json");
    }

    private Stream<DynamicTest> cases(String file) throws IOException {
        JsonNode golden = read(file);
        Ruleset ruleset = PolicyFormat.ruleset(read("policies/" + golden.get("ruleset").asText() + ".json"));
        Scorecard scorecard = PolicyFormat.scorecard(read("policies/" + golden.get("scorecard").asText() + ".json"));
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : golden.get("cases")) {
            ObjectNode facts = golden.get("base").deepCopy();
            facts.setAll((ObjectNode) c.get("given"));
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () ->
                check(DecisionEngine.decide(ruleset, scorecard, MAPPER.treeToValue(facts, CreditFacts.class)),
                    c.get("expect"))));
        }
        return tests.stream();
    }

    private static void check(CreditDecision d, JsonNode expect) {
        assertThat(d.outcome().name()).isEqualTo(expect.get("outcome").asText());
        assertThat(d.reasons()).containsExactlyElementsOf(
            MAPPER.convertValue(expect.get("reasons"), MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)));
        if (expect.has("score")) {
            assertThat(d.score().score()).isEqualTo(expect.get("score").asInt());
        }
        if (expect.has("grade")) {
            assertThat(d.score().grade()).isEqualTo(expect.get("grade").asText());
        }
        if (expect.has("pd")) {
            assertThat(d.score().probabilityOfDefault()).isEqualByComparingTo(expect.get("pd").asText());
        }
        if (expect.has("annualRate")) {
            assertThat(d.annualRate()).isEqualByComparingTo(expect.get("annualRate").asText());
        }
        if (expect.has("approvedAmount")) {
            JsonNode amount = expect.get("approvedAmount");
            if (amount.isNull()) {
                assertThat(d.approvedAmount()).isNull();
            } else {
                assertThat(d.approvedAmount()).isEqualByComparingTo(amount.asText());
            }
        }
        if (expect.has("emi")) {
            assertThat(d.emi()).isEqualByComparingTo(new BigDecimal(expect.get("emi").asText()));
        }
    }

    private static JsonNode read(String resource) throws IOException {
        try (InputStream in = GoldenDecisionTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing " + resource);
            }
            return MAPPER.readTree(in);
        }
    }
}
