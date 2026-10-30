package com.lendflow.underwriting.domain;

import com.lendflow.shared.domain.Actor.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One step of a manual review. A referred application gets a MAKER task; the maker's recommendation opens a
 * CHECKER task for someone else with enough authority for the amount.
 *
 * @param action what the assignee did on completion: APPROVE or DECLINE for a maker, CONFIRM or SEND_BACK
 *               for a checker
 */
public record Task(UUID id, UUID applicationId, TaskStage stage, Role role, TaskStatus status, String assignee,
                   String maker, Recommendation recommendation, BigDecimal amount, String action, String note,
                   Instant dueAt, int escalations, Instant createdAt, Instant claimedAt, Instant completedAt) {

    public static Task maker(UUID id, UUID applicationId, Instant now, Instant dueAt) {
        return new Task(id, applicationId, TaskStage.MAKER, Role.UNDERWRITER, TaskStatus.OPEN, null, null, null, null,
            null, null, dueAt, 0, now, null, null);
    }

    public Task checker(UUID id, Role checkerRole, Recommendation recommendation, BigDecimal amount, Instant now,
                        Instant dueAt) {
        return new Task(id, applicationId, TaskStage.CHECKER, checkerRole, TaskStatus.OPEN, null, assignee,
            recommendation, amount, null, null, dueAt, 0, now, null, null);
    }
}
