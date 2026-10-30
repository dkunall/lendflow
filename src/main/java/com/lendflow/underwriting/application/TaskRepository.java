package com.lendflow.underwriting.application;

import com.lendflow.shared.domain.Actor.Role;
import com.lendflow.underwriting.domain.Task;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    /** @return false if the application already has a task that is not done */
    boolean insertIfNoneActive(Task task);

    Optional<Task> find(UUID id);

    Optional<Task> lock(UUID id);

    /**
     * Claims an open task in one conditional update, so two people pressing claim at once cannot both get it.
     *
     * @return false if the task is not open, needs a role not in {@code roles}, or was made by {@code who}
     */
    boolean claim(UUID id, String who, Collection<Role> roles, Instant now);

    void complete(UUID id, String action, String note, Instant now);

    /** Open tasks these roles may claim, oldest first, plus what {@code who} already holds. */
    List<Task> queue(String who, Collection<Role> roles, int limit);

    List<Task> forApplication(UUID applicationId);

    /** Overdue tasks that no other sweeper holds, locked until the transaction ends. */
    List<Task> lockOverdue(Instant now, int limit);

    void escalate(UUID id, Role role, Instant dueAt);
}
