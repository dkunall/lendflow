package com.lendflow.decision.adapter.web;

import com.lendflow.decision.application.DecisionService;
import com.lendflow.decision.application.ShadowReport;
import com.lendflow.shared.domain.Actor;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DecisionController {

    private final DecisionService decisions;

    DecisionController(DecisionService decisions) {
        this.decisions = decisions;
    }

    @GetMapping("/v1/applications/{id}/decision")
    Object decision(Actor actor, @PathVariable UUID id) {
        return decisions.view(actor, id);
    }

    @GetMapping("/v1/shadow-report")
    ShadowReport shadowReport(@RequestParam(defaultValue = "PERSONAL-v1") String ruleset) {
        return decisions.shadowReport(ruleset);
    }
}
