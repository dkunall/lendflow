package com.lendflow.workflow.application;

import com.lendflow.application.application.SubmissionListener;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Queues the checks in the submit transaction, so a submitted application always has a job. */
@Component
class AssessmentQueue implements SubmissionListener {

    private final JobRepository jobs;
    private final Clock clock;

    AssessmentQueue(JobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override
    public void submitted(UUID applicationId) {
        jobs.enqueue(applicationId, clock.instant());
    }
}
