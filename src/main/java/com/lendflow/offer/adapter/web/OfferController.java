package com.lendflow.offer.adapter.web;

import com.lendflow.offer.application.OfferService;
import com.lendflow.offer.application.OfferView;
import com.lendflow.offer.application.Schedule;
import com.lendflow.shared.domain.Actor;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OfferController {

    private final OfferService offers;

    OfferController(OfferService offers) {
        this.offers = offers;
    }

    @GetMapping("/v1/offers/{id}")
    OfferView offer(Actor actor, @PathVariable UUID id) {
        return offers.get(actor, id);
    }

    @GetMapping("/v1/applications/{id}/offer")
    OfferView forApplication(Actor actor, @PathVariable UUID id) {
        return offers.forApplication(actor, id);
    }

    @GetMapping("/v1/offers/{id}/schedule")
    Schedule schedule(Actor actor, @PathVariable UUID id) {
        return offers.schedule(actor, id);
    }

    @PostMapping("/v1/offers/{id}/accept")
    OfferView accept(Actor actor, @PathVariable UUID id,
                     @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        return offers.accept(actor, id, key);
    }

    @GetMapping(value = "/v1/offers/{id}/sanction-letter", produces = MediaType.TEXT_PLAIN_VALUE)
    String sanctionLetter(Actor actor, @PathVariable UUID id) {
        return offers.sanctionLetter(actor, id);
    }
}
