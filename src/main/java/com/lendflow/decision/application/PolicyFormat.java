package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.decision.domain.Condition;
import com.lendflow.decision.domain.DecisionTable;
import com.lendflow.decision.domain.HitPolicy;
import com.lendflow.decision.domain.Rule;
import com.lendflow.decision.domain.Ruleset;
import com.lendflow.decision.domain.Scorecard;
import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the JSON that rulesets and scorecards are stored and published as. Anything malformed throws
 * {@link IllegalArgumentException} with the path of the bad element, so a policy author sees what to fix.
 */
public final class PolicyFormat {

    private PolicyFormat() {
    }

    public static Ruleset ruleset(JsonNode root) {
        JsonNode p = required(root, "parameters");
        Ruleset.Parameters parameters = new Ruleset.Parameters(
            decimal(p, "foirCap"), optionalDecimal(p, "maxLtv"), decimal(p, "minAmount"), decimal(p, "maxAmount"),
            decimal(p, "amountStep"), decimal(p, "autoApproveLimit"), required(p, "reasonLimit").asInt());
        return new Ruleset(Product.valueOf(text(root, "product")), required(root, "version").asInt(), parameters,
            text(root, "champion"), root.path("challenger").isTextual() ? root.get("challenger").asText() : null,
            table("eligibility", required(root, "eligibility")), table("decision", required(root, "decision")),
            table("pricing", required(root, "pricing")));
    }

    public static Scorecard scorecard(JsonNode root) {
        String id = text(root, "id");
        List<Scorecard.Characteristic> characteristics = new ArrayList<>();
        for (JsonNode c : required(root, "characteristics")) {
            List<Scorecard.Bin> bins = new ArrayList<>();
            for (JsonNode b : required(c, "bins")) {
                bins.add(new Scorecard.Bin(condition(id + "/" + c.path("name").asText(), b.get(0)), b.get(1).asInt(),
                    b.has(2) ? b.get(2).asText() : null));
            }
            characteristics.add(new Scorecard.Characteristic(text(c, "name"), text(c, "fact"), text(c, "reason"), bins));
        }
        List<Scorecard.Grade> grades = new ArrayList<>();
        required(root, "grades").fields().forEachRemaining(g -> grades.add(new Scorecard.Grade(g.getKey(), g.getValue().asInt())));
        JsonNode cal = required(root, "calibration");
        return new Scorecard(id, characteristics, required(cal, "baseScore").asInt(), required(cal, "baseOdds").asDouble(),
            required(cal, "pdo").asInt(), grades);
    }

    private static DecisionTable table(String name, JsonNode t) {
        List<String> inputs = strings(required(t, "inputs"));
        List<String> outputs = strings(required(t, "outputs"));
        List<Rule> rules = new ArrayList<>();
        for (JsonNode r : required(t, "rules")) {
            String id = text(r, "id");
            List<Condition> when = new ArrayList<>();
            for (JsonNode cell : required(r, "when")) {
                when.add(condition(name + "/" + id, cell));
            }
            Map<String, Object> then = new LinkedHashMap<>();
            required(r, "then").fields().forEachRemaining(e -> then.put(e.getKey(), e.getValue().asText()));
            rules.add(new Rule(id, when, then));
        }
        JsonNode priority = t.path("priority");
        return new DecisionTable(name, HitPolicy.valueOf(text(t, "hitPolicy")), inputs, outputs,
            priority.path("output").isTextual() ? priority.get("output").asText() : null,
            priority.has("order") ? strings(priority.get("order")) : null, rules);
    }

    private static Condition condition(String where, JsonNode cell) {
        try {
            return Condition.parse(cell.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing '" + field + "'");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        return required(node, field).asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(text(node, field));
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : new BigDecimal(value.asText());
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
