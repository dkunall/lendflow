package com.lendflow.application.application;

import com.lendflow.application.domain.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record HistoryEntry(UUID applicationId, ApplicationStatus from, ApplicationStatus to, String event,
                           String actor, String reason, Instant at) {
}
