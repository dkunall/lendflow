package com.lendflow.workflow.adapter.persistence;

import com.lendflow.workflow.application.AssessmentJob;
import com.lendflow.workflow.application.JobRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcJobRepository implements JobRepository {

    private static final int MAX_ERROR = 500;

    private final JdbcClient jdbc;

    JdbcJobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(UUID applicationId, Instant runAfter) {
        jdbc.sql("""
                INSERT INTO workflow_jobs (id, application_id, status, run_after)
                VALUES (?, ?, 'PENDING', ?)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(UUID.randomUUID(), applicationId, Timestamp.from(runAfter))
            .update();
    }

    @Override
    public List<AssessmentJob> claim(int limit, Duration lease) {
        return jdbc.sql("""
                UPDATE workflow_jobs j
                SET status = 'RUNNING', attempts = j.attempts + 1, locked_until = now() + make_interval(secs => ?),
                    updated_at = now()
                FROM (SELECT id FROM workflow_jobs
                      WHERE (status = 'PENDING' AND run_after <= now())
                         OR (status = 'RUNNING' AND locked_until < now())
                      ORDER BY run_after
                      LIMIT ?
                      FOR UPDATE SKIP LOCKED) due
                WHERE j.id = due.id
                RETURNING j.id, j.application_id, j.attempts
                """)
            .params(lease.toMillis() / 1000.0, limit)
            .query((rs, row) -> new AssessmentJob(rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class), rs.getInt("attempts")))
            .list();
    }

    @Override
    public void retryLater(UUID jobId, Instant runAfter, String error) {
        jdbc.sql("""
                UPDATE workflow_jobs SET status = 'PENDING', run_after = ?, locked_until = NULL, last_error = ?,
                    updated_at = now()
                WHERE id = ? AND status = 'RUNNING'
                """)
            .params(Timestamp.from(runAfter), truncate(error), jobId)
            .update();
    }

    @Override
    public void complete(UUID jobId) {
        jdbc.sql("UPDATE workflow_jobs SET status = 'DONE', locked_until = NULL, updated_at = now() WHERE id = ?")
            .param(jobId)
            .update();
    }

    @Override
    public void fail(UUID jobId, String error) {
        jdbc.sql("""
                UPDATE workflow_jobs SET status = 'FAILED', locked_until = NULL, last_error = ?, updated_at = now()
                WHERE id = ? AND status <> 'DONE'
                """)
            .params(truncate(error), jobId)
            .update();
    }

    private static String truncate(String error) {
        return error == null || error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
    }
}
