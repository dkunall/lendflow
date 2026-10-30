CREATE TABLE credit_reports (
    application_id    uuid          PRIMARY KEY REFERENCES applications (id),
    bureau_ref        text          NOT NULL,
    hit               boolean       NOT NULL,
    score             integer       CHECK (score BETWEEN 300 AND 900),
    active_accounts   integer       NOT NULL,
    total_outstanding numeric(14,2) NOT NULL,
    max_dpd_12m       integer       NOT NULL,
    enquiries_6m      integer       NOT NULL,
    months_on_bureau  integer       NOT NULL,
    written_off       boolean       NOT NULL,
    reported_emi      numeric(12,2) NOT NULL,
    consent_version   text          NOT NULL,
    pulled_at         timestamptz   NOT NULL,
    CHECK (hit OR score IS NULL)
);
