package com.lendflow.underwriting.adapter.persistence;

import com.lendflow.shared.domain.Actor.Role;
import com.lendflow.underwriting.application.TaskRepository;
import com.lendflow.underwriting.domain.Recommendation;
import com.lendflow.underwriting.domain.Task;
import com.lendflow.underwriting.domain.TaskStage;
import com.lendflow.underwriting.domain.TaskStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaskRepository implements TaskRepository {

    private static final String COLUMNS = """
        id, application_id, stage, role, status, assignee, maker, recommendation, amount, action, note, due_at,
        escalations, created_at, claimed_at, completed_at
        """;

    private final JdbcClient jdbc;

    JdbcTaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfNoneActive(Task t) {
        return jdbc.sql("""
                INSERT INTO underwriting_tasks (id, application_id, stage, role, status, maker, recommendation, amount,
                    due_at, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)
                ON CONFLICT (application_id) WHERE status <> 'DONE' DO NOTHING
                """)
            .params(t.id(), t.applicationId(), t.stage().name(), t.role().name(), t.maker(),
                t.recommendation() == null ? null : t.recommendation().name(), t.amount(), Timestamp.from(t.dueAt()),
                Timestamp.from(t.createdAt()))
            .update() == 1;
    }

    @Override
    public Optional<Task> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM underwriting_tasks WHERE id = ?").param(id).query(this::map)
            .optional();
    }

    @Override
    public Optional<Task> lock(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM underwriting_tasks WHERE id = ? FOR UPDATE").param(id)
            .query(this::map).optional();
    }

    @Override
    public boolean claim(UUID id, String who, Collection<Role> roles, Instant now) {
        if (roles.isEmpty()) {
            return false;
        }
        return jdbc.sql("""
                UPDATE underwriting_tasks SET status = 'CLAIMED', assignee = :who, claimed_at = :now
                WHERE id = :id AND status = 'OPEN' AND role IN (:roles) AND maker IS DISTINCT FROM :who
                """)
            .param("who", who).param("now", Timestamp.from(now)).param("id", id).param("roles", names(roles))
            .update() == 1;
    }

    @Override
    public void complete(UUID id, String action, String note, Instant now) {
        jdbc.sql("""
                UPDATE underwriting_tasks SET status = 'DONE', action = ?, note = ?, completed_at = ?
                WHERE id = ? AND status = 'CLAIMED'
                """)
            .params(action, note, Timestamp.from(now), id)
            .update();
    }

    @Override
    public List<Task> queue(String who, Collection<Role> roles, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                FROM underwriting_tasks
                WHERE (status = 'OPEN' AND role IN (:roles) AND maker IS DISTINCT FROM :who)
                   OR (status = 'CLAIMED' AND assignee = :who)
                ORDER BY due_at
                LIMIT :limit
                """)
            .param("roles", roles.isEmpty() ? List.of("") : names(roles)).param("who", who).param("limit", limit)
            .query(this::map).list();
    }

    @Override
    public List<Task> forApplication(UUID applicationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM underwriting_tasks WHERE application_id = ? ORDER BY created_at")
            .param(applicationId).query(this::map).list();
    }

    @Override
    public List<Task> lockOverdue(Instant now, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                FROM underwriting_tasks
                WHERE status <> 'DONE' AND due_at < ?
                ORDER BY due_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """)
            .params(Timestamp.from(now), limit).query(this::map).list();
    }

    @Override
    public void escalate(UUID id, Role role, Instant dueAt) {
        jdbc.sql("""
                UPDATE underwriting_tasks
                SET role = ?, status = 'OPEN', assignee = NULL, claimed_at = NULL, due_at = ?, escalations = escalations + 1
                WHERE id = ?
                """)
            .params(role.name(), Timestamp.from(dueAt), id)
            .update();
    }

    private static List<String> names(Collection<Role> roles) {
        return roles.stream().map(Role::name).toList();
    }

    private Task map(ResultSet rs, int row) throws SQLException {
        String recommendation = rs.getString("recommendation");
        return new Task(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
            TaskStage.valueOf(rs.getString("stage")), Role.valueOf(rs.getString("role")),
            TaskStatus.valueOf(rs.getString("status")), rs.getString("assignee"), rs.getString("maker"),
            recommendation == null ? null : Recommendation.valueOf(recommendation), rs.getBigDecimal("amount"),
            rs.getString("action"), rs.getString("note"), instant(rs, "due_at"), rs.getInt("escalations"),
            instant(rs, "created_at"), instant(rs, "claimed_at"), instant(rs, "completed_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
