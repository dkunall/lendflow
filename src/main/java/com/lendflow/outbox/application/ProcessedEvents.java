package com.lendflow.outbox.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side half of effectively-once: claim the event id in the same transaction as the effect, and skip
 * the effect when the claim was already there.
 */
@Component
public class ProcessedEvents {

    private final JdbcClient jdbc;
    private final MeterRegistry meters;

    ProcessedEvents(JdbcClient jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, UUID eventId) {
        boolean first = jdbc.sql("INSERT INTO processed_events (consumer, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING")
            .params(consumer, eventId)
            .update() == 1;
        if (!first) {
            meters.counter("lendflow.events.duplicates", "consumer", consumer).increment();
        }
        return first;
    }
}
