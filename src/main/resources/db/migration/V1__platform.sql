CREATE TABLE outbox (
    id           bigserial   PRIMARY KEY,
    event_id     uuid        NOT NULL UNIQUE,
    topic        text        NOT NULL,
    message_key  text        NOT NULL,
    event_type   text        NOT NULL,
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);

CREATE INDEX outbox_unpublished_idx ON outbox (id) WHERE published_at IS NULL;

-- Consumers record what they applied in the same transaction as the effect, so redelivery is a no-op.
CREATE TABLE processed_events (
    consumer   text        NOT NULL,
    event_id   uuid        NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

-- Written in the same transaction as the effect it guards. A concurrent insert of the same key blocks on the
-- primary key until the first transaction ends, then sees the committed response.
CREATE TABLE idempotency_keys (
    scope        text        NOT NULL,
    idem_key     text        NOT NULL,
    request_hash text        NOT NULL,
    response     jsonb,
    created_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, idem_key)
);
