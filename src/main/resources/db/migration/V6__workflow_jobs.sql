CREATE TABLE workflow_jobs (
    id              uuid         PRIMARY KEY,
    application_id  uuid         NOT NULL UNIQUE REFERENCES applications (id),
    status          text         NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts        integer      NOT NULL DEFAULT 0,
    run_after       timestamptz  NOT NULL,
    locked_until    timestamptz,
    last_error      text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX workflow_jobs_due ON workflow_jobs (run_after) WHERE status IN ('PENDING', 'RUNNING');
