package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lendflow.decision.application.PolicyFormat;
import com.lendflow.decision.domain.Ruleset;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class PolicyFormatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsTheSeededPersonalLoanPolicy() throws Exception {
        Ruleset ruleset = PolicyFormat.ruleset(load("policies/personal-v1.json"));

        assertThat(ruleset.key()).isEqualTo("PERSONAL-v1");
        assertThat(ruleset.championScorecard()).isEqualTo("PL-SC-1");
        assertThat(ruleset.challengerScorecard()).isEqualTo("PL-SC-2");
        assertThat(ruleset.parameters().maxLtv()).isNull();
    }

    @Test
    void pointsAtTheRuleWithABadCell() throws Exception {
        ObjectNode policy = (ObjectNode) load("policies/personal-v1.json");
        ArrayNode when = (ArrayNode) policy.at("/eligibility/rules/0/when");
        when.set(0, mapper.getNodeFactory().textNode("[30..20]"));

        assertThatThrownBy(() -> PolicyFormat.ruleset(policy))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("eligibility/E1");
    }

    @Test
    void securedProductWithoutLtvIsRejected() throws Exception {
        ObjectNode policy = (ObjectNode) load("policies/home-v1.json");
        ((ObjectNode) policy.get("parameters")).remove("maxLtv");

        assertThatThrownBy(() -> PolicyFormat.ruleset(policy)).isInstanceOf(IllegalArgumentException.class);
    }

    private com.fasterxml.jackson.databind.JsonNode load(String resource) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            return mapper.readTree(in);
        }
    }
}
