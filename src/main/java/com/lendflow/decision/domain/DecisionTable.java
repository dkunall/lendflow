package com.lendflow.decision.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record DecisionTable(String name, HitPolicy hitPolicy, List<String> inputs, List<String> outputs,
                            String priorityOutput, List<String> priorityOrder, List<Rule> rules) {

    public DecisionTable {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        priorityOrder = priorityOrder == null ? List.of() : List.copyOf(priorityOrder);
        rules = List.copyOf(rules);
        for (Rule rule : rules) {
            if (rule.when().size() != inputs.size()) {
                throw new IllegalArgumentException(name + "/" + rule.id() + ": expected " + inputs.size()
                    + " conditions, got " + rule.when().size());
            }
            if (!Set.copyOf(outputs).equals(rule.then().keySet())) {
                throw new IllegalArgumentException(name + "/" + rule.id() + ": outputs must be " + outputs);
            }
        }
        if (hitPolicy == HitPolicy.PRIORITY) {
            if (priorityOutput == null || !outputs.contains(priorityOutput)) {
                throw new IllegalArgumentException(name + ": PRIORITY needs a priority output");
            }
            for (Rule rule : rules) {
                if (!priorityOrder.contains(String.valueOf(rule.then().get(priorityOutput)))) {
                    throw new IllegalArgumentException(name + "/" + rule.id() + ": "
                        + rule.then().get(priorityOutput) + " is not in the priority list");
                }
            }
        }
    }

    public List<Rule> evaluate(Map<String, Object> facts) {
        List<Rule> matched = rules.stream().filter(r -> r.matches(inputs, facts)).toList();
        return switch (hitPolicy) {
            case FIRST -> matched.isEmpty() ? List.of() : List.of(matched.getFirst());
            case COLLECT -> matched;
            case PRIORITY -> {
                int best = matched.stream().mapToInt(this::rank).min().orElse(-1);
                yield matched.stream().filter(r -> rank(r) == best).toList();
            }
        };
    }

    private int rank(Rule rule) {
        return priorityOrder.indexOf(String.valueOf(rule.then().get(priorityOutput)));
    }
}
