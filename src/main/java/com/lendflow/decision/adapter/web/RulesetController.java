package com.lendflow.decision.adapter.web;

import com.lendflow.decision.application.RulesetService;
import com.lendflow.decision.application.RulesetService.Draft;
import com.lendflow.decision.application.RulesetService.SimulationResult;
import com.lendflow.decision.application.StoredRuleset;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.domain.Product;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RulesetController {

    private final RulesetService rulesets;

    RulesetController(RulesetService rulesets) {
        this.rulesets = rulesets;
    }

    @PostMapping("/v1/rulesets")
    @ResponseStatus(HttpStatus.CREATED)
    StoredRuleset draft(Actor actor, @RequestBody(required = false) Draft draft) {
        return rulesets.draft(actor, draft);
    }

    @GetMapping("/v1/rulesets")
    List<StoredRuleset> list(@RequestParam Product product) {
        return rulesets.list(product);
    }

    @GetMapping("/v1/rulesets/{id}")
    StoredRuleset get(@PathVariable UUID id) {
        return rulesets.get(id);
    }

    @PostMapping("/v1/rulesets/{id}/simulate")
    SimulationResult simulate(@PathVariable UUID id, @RequestParam(defaultValue = "1000") int limit) {
        return rulesets.simulate(id, limit);
    }

    @PostMapping("/v1/rulesets/{id}/publish")
    StoredRuleset publish(Actor actor, @PathVariable UUID id) {
        return rulesets.publish(actor, id);
    }
}
