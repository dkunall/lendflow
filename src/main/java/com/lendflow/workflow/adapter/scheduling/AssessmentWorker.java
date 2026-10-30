package com.lendflow.workflow.adapter.scheduling;

import com.lendflow.workflow.application.Assessment;
import com.lendflow.workflow.application.AssessmentJob;
import com.lendflow.workflow.application.JobRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for due jobs and runs a batch in parallel, waiting for the batch before the next poll. Any number of
 * instances can run this; the lease and SKIP LOCKED keep them off each other's jobs.
 */
@Component
@ConditionalOnProperty(name = "lendflow.workflow.enabled", havingValue = "true", matchIfMissing = true)
class AssessmentWorker {

    private final JobRepository jobs;
    private final Assessment assessment;
    private final int batchSize;
    private final Duration lease;

    AssessmentWorker(JobRepository jobs, Assessment assessment,
                     @Value("${lendflow.workflow.batch-size:8}") int batchSize,
                     @Value("${lendflow.workflow.lease:60s}") Duration lease) {
        this.jobs = jobs;
        this.assessment = assessment;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${lendflow.workflow.poll-ms:500}")
    void poll() {
        List<AssessmentJob> due = jobs.claim(batchSize, lease);
        if (due.isEmpty()) {
            return;
        }
        try (ExecutorService runner = Executors.newVirtualThreadPerTaskExecutor()) {
            due.forEach(job -> runner.submit(() -> assessment.run(job)));
        }
    }
}
