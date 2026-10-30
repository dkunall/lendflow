package com.lendflow.application.application;

import java.util.UUID;

/**
 * Told about a submission inside the submitting transaction, so the work it queues commits or rolls back
 * with the status change.
 */
public interface SubmissionListener {

    void submitted(UUID applicationId);
}
