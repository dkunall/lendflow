CREATE TABLE underwriting_tasks (
    id             uuid          PRIMARY KEY,
    application_id uuid          NOT NULL REFERENCES applications (id),
    stage          text          NOT NULL CHECK (stage IN ('MAKER', 'CHECKER')),
    role           text          NOT NULL CHECK (role IN ('UNDERWRITER', 'CREDIT_MANAGER', 'CREDIT_HEAD')),
    status         text          NOT NULL CHECK (status IN ('OPEN', 'CLAIMED', 'DONE')),
    assignee       text,
    maker          text,
    recommendation text          CHECK (recommendation IN ('APPROVE', 'DECLINE')),
    amount         numeric(14,2),
    action         text,
    note           text,
    due_at         timestamptz   NOT NULL,
    escalations    int           NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL,
    claimed_at     timestamptz,
    completed_at   timestamptz,
    CHECK ((status = 'OPEN') = (assignee IS NULL)),
    CHECK ((status = 'DONE') = (completed_at IS NOT NULL)),
    CHECK (stage = 'MAKER' OR (maker IS NOT NULL AND recommendation IS NOT NULL)),
    -- four eyes, enforced again here so no code path can let the maker check their own work
    CHECK (stage = 'MAKER' OR assignee IS NULL OR assignee <> maker)
);

CREATE UNIQUE INDEX underwriting_tasks_one_active ON underwriting_tasks (application_id) WHERE status <> 'DONE';
CREATE INDEX underwriting_tasks_queue ON underwriting_tasks (role, created_at) WHERE status = 'OPEN';
CREATE INDEX underwriting_tasks_due ON underwriting_tasks (due_at) WHERE status <> 'DONE';
