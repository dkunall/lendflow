package com.lendflow.disbursement.adapter.web;

import com.lendflow.disbursement.application.DisbursementService;
import com.lendflow.disbursement.domain.Disbursement;
import com.lendflow.shared.domain.Actor;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DisbursementController {

    record Confirmation(String utr) {
    }

    private final DisbursementService disbursements;

    DisbursementController(DisbursementService disbursements) {
        this.disbursements = disbursements;
    }

    @GetMapping("/v1/disbursements")
    List<Disbursement> pending(@RequestParam(required = false) UUID application,
                               @RequestParam(defaultValue = "50") int limit) {
        return application == null ? disbursements.pending(limit) : disbursements.forApplication(application).stream().toList();
    }

    @GetMapping("/v1/disbursements/{id}")
    Disbursement get(@PathVariable UUID id) {
        return disbursements.get(id);
    }

    @PostMapping("/v1/disbursements/{id}/confirm")
    Disbursement confirm(Actor actor, @PathVariable UUID id, @RequestBody(required = false) Confirmation body) {
        return disbursements.confirm(actor, id, body == null ? null : body.utr());
    }
}
