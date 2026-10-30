package com.lendflow.decision.domain;

import java.util.List;
import java.util.Map;

public record Rule(String id, List<Condition> when, Map<String, Object> then) {

    public Rule {
        when = List.copyOf(when);
        then = Map.copyOf(then);
    }

    boolean matches(List<String> inputs, Map<String, Object> facts) {
        for (int i = 0; i < inputs.size(); i++) {
            if (!when.get(i).matches(facts.get(inputs.get(i)))) {
                return false;
            }
        }
        return true;
    }
}
