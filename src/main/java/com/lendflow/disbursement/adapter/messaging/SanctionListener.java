package com.lendflow.disbursement.adapter.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendflow.disbursement.application.DisbursementService;
import com.lendflow.disbursement.application.SanctionedLoan;
import com.lendflow.shared.messaging.EventEnvelope;
import com.lendflow.shared.messaging.Topics;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class SanctionListener {

    private final DisbursementService disbursements;
    private final ObjectMapper json;

    SanctionListener(DisbursementService disbursements, ObjectMapper json) {
        this.disbursements = disbursements;
        this.json = json;
    }

    @KafkaListener(topics = Topics.SANCTIONS, groupId = "lendflow-disbursement")
    void onMessage(String message) throws JsonProcessingException {
        EventEnvelope event = json.readValue(message, EventEnvelope.class);
        if (!"LoanSanctioned".equals(event.eventType())) {
            return;
        }
        JsonNode p = event.payload();
        disbursements.onSanctioned(event.eventId(), new SanctionedLoan(
            UUID.fromString(p.path("applicationId").asText()), UUID.fromString(p.path("offerId").asText()),
            p.path("product").asText(), p.path("netDisbursal").decimalValue()));
    }
}
