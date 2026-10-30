-- Kept in Postgres to keep the demo to one stateful service; 2 MB per file and three files per application.
CREATE TABLE documents (
    id             uuid        PRIMARY KEY,
    application_id uuid        NOT NULL REFERENCES applications (id),
    type           text        NOT NULL CHECK (type IN ('PAN_CARD', 'INCOME_PROOF', 'PROPERTY_DOCUMENT')),
    file_name      text        NOT NULL,
    media_type     text        NOT NULL,
    size_bytes     bigint      NOT NULL CHECK (size_bytes > 0),
    sha256         text        NOT NULL,
    content        bytea       NOT NULL,
    uploaded_by    text        NOT NULL,
    uploaded_at    timestamptz NOT NULL,
    superseded_at  timestamptz
);

CREATE UNIQUE INDEX documents_current_idx ON documents (application_id, type) WHERE superseded_at IS NULL;

CREATE TABLE kyc_checks (
    application_id uuid         PRIMARY KEY REFERENCES applications (id),
    outcome        text         NOT NULL CHECK (outcome IN ('PASS', 'REFER', 'FAIL')),
    name_score     numeric(5,4) NOT NULL,
    reasons        text[]       NOT NULL,
    checked_at     timestamptz  NOT NULL
);
