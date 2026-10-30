package com.lendflow.underwriting.adapter.scheduling;

import com.lendflow.underwriting.application.UnderwritingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lendflow.underwriting.sweep-enabled", havingValue = "true", matchIfMissing = true)
class SlaSweeper {

    private final UnderwritingService underwriting;
    private final int batchSize;

    SlaSweeper(UnderwritingService underwriting, @Value("${lendflow.underwriting.sweep-batch:50}") int batchSize) {
        this.underwriting = underwriting;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${lendflow.underwriting.sweep-ms:60000}")
    void sweep() {
        int escalated;
        do {
            escalated = underwriting.escalateOverdue(batchSize);
        } while (escalated == batchSize);
    }
}
