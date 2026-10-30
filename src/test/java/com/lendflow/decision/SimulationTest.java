package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lendflow.decision.application.PolicyFormat;
import com.lendflow.decision.domain.CreditFacts;
import com.lendflow.decision.domain.Outcome;
import com.lendflow.decision.domain.Ruleset;
import com.lendflow.decision.domain.Scorecard;
import com.lendflow.decision.domain.Simulation;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Uses the golden personal-loan cases as a small recorded history. */
class SimulationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Scorecard scorecard = PolicyFormat.scorecard(read("policies/PL-SC-1.json"));
    private final List<Simulation.Past> history = history();

    @Test
    void replayingThePolicyThatMadeTheDecisionsChangesNothing() {
        Simulation.Report report = Simulation.run(PolicyFormat.ruleset(read("policies/personal-v1.json")), scorecard,
            history, 5);

        assertThat(report.evaluated()).isEqualTo(history.size());
        assertThat(report.changed()).isZero();
        report.matrix().forEach((from, to) -> assertThat(to.keySet()).containsOnly(from));
    }

    @Test
    void loweringTheAutoApprovalLimitRefersEveryApproval() {
        ObjectNode policy = (ObjectNode) read("policies/personal-v1.json");
        ((ObjectNode) policy.get("parameters")).put("autoApproveLimit", "1000");
        Ruleset stricter = PolicyFormat.ruleset(policy);
        long approvals = history.stream().filter(p -> p.outcome() == Outcome.APPROVE).count();

        Simulation.Report report = Simulation.run(stricter, scorecard, history, 2);

        assertThat(approvals).isPositive();
        assertThat(report.changed()).isEqualTo((int) approvals);
        assertThat(report.matrix().get(Outcome.APPROVE)).isEqualTo(Map.of(Outcome.REFER, (int) approvals));
        assertThat(report.sample()).hasSize((int) Math.min(2, approvals))
            .allSatisfy(c -> assertThat(c.reasons()).contains("ABOVE_AUTO_APPROVAL_LIMIT"));
    }

    private static List<Simulation.Past> history() {
        JsonNode golden = read("golden/personal.json");
        List<Simulation.Past> past = new ArrayList<>();
        for (JsonNode c : golden.get("cases")) {
            ObjectNode facts = golden.get("base").deepCopy();
            facts.setAll((ObjectNode) c.get("given"));
            past.add(new Simulation.Past(UUID.randomUUID(), Outcome.valueOf(c.get("expect").get("outcome").asText()),
                MAPPER.convertValue(facts, CreditFacts.class)));
        }
        return past;
    }

    private static JsonNode read(String path) {
        try (InputStream in = SimulationTest.class.getClassLoader().getResourceAsStream(path)) {
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
