package com.lendflow.workflow.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobRepository {

    void enqueue(UUID applicationId, Instant runAfter);

    /**
     * Leases up to {@code limit} due jobs, including running ones whose lease ran out because their worker
     * died. Rows another worker is claiming at the same moment are skipped, not waited for.
     */
    List<AssessmentJob> claim(int limit, Duration lease);

    void retryLater(UUID jobId, Instant runAfter, String error);

    void complete(UUID jobId);

    void fail(UUID jobId, String error);
}
