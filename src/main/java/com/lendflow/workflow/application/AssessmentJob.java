package com.lendflow.workflow.application;

import java.util.UUID;

/** A claimed job; {@code attempt} counts this run, starting at 1. */
public record AssessmentJob(UUID id, UUID applicationId, int attempt) {
}
