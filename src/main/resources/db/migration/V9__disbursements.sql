CREATE TABLE disbursements (
    id             uuid          PRIMARY KEY,
    application_id uuid          NOT NULL UNIQUE,
    offer_id       uuid          NOT NULL,
    product        text          NOT NULL,
    amount         numeric(14,2) NOT NULL CHECK (amount > 0),
    status         text          NOT NULL CHECK (status IN ('REQUESTED', 'SENT')),
    utr            text          UNIQUE,
    requested_at   timestamptz   NOT NULL,
    sent_at        timestamptz,
    sent_by        text,
    CHECK ((status = 'SENT') = (utr IS NOT NULL AND sent_at IS NOT NULL AND sent_by IS NOT NULL))
);

-- Deliberately no foreign key to applications: this table is filled from the sanctions topic only.
CREATE INDEX disbursements_pending ON disbursements (requested_at) WHERE status = 'REQUESTED';
