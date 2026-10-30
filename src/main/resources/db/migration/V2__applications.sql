CREATE TABLE applications (
    id                uuid          PRIMARY KEY,
    owner_subject     text          NOT NULL,
    product           text          NOT NULL CHECK (product IN ('PERSONAL', 'HOME')),
    amount            numeric(14,2) CHECK (amount > 0),
    tenure_months     int           CHECK (tenure_months > 0),
    monthly_income    numeric(14,2) CHECK (monthly_income > 0),
    existing_emi      numeric(14,2) CHECK (existing_emi >= 0),
    employment_type   text          CHECK (employment_type IN ('SALARIED', 'SELF_EMPLOYED')),
    months_in_job     int           CHECK (months_in_job >= 0),
    property_value    numeric(14,2) CHECK (property_value > 0),
    consent_at        timestamptz,
    consent_version   text,
    status            text          NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDERWRITING', 'APPROVED',
                                        'REFER', 'REJECTED', 'SANCTIONED', 'DISBURSED', 'WITHDRAWN')),
    version           int           NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    submitted_at      timestamptz
);

CREATE INDEX applications_owner_idx ON applications (owner_subject, created_at DESC);

-- Append-only: every status change, who caused it and why.
CREATE TABLE application_history (
    id             bigserial   PRIMARY KEY,
    application_id uuid        NOT NULL REFERENCES applications (id),
    from_status    text,
    to_status      text        NOT NULL,
    event          text        NOT NULL,
    actor          text        NOT NULL,
    reason         text,
    at             timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX application_history_app_idx ON application_history (application_id, id);

-- One row per application. PAN, phone and email are stored as a keyed HMAC for lookups plus an AES-GCM
-- ciphertext; a plain hash of a 10 digit phone number can be reversed by brute force.
CREATE TABLE applicants (
    application_id  uuid        PRIMARY KEY REFERENCES applications (id),
    owner_subject   text        NOT NULL,
    full_name       text,
    comparable_name text,
    date_of_birth   date,
    pan_hmac        text,
    pan_enc         text,
    phone_hmac      text,
    phone_enc       text,
    email_hmac      text,
    email_enc       text,
    submitted_at    timestamptz,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX applicants_pan_idx ON applicants (pan_hmac) WHERE submitted_at IS NOT NULL;
CREATE INDEX applicants_phone_idx ON applicants (phone_hmac) WHERE submitted_at IS NOT NULL;
CREATE INDEX applicants_email_idx ON applicants (email_hmac) WHERE submitted_at IS NOT NULL;
CREATE INDEX applicants_dob_idx ON applicants (date_of_birth) WHERE submitted_at IS NOT NULL;
