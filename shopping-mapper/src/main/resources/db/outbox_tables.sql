CREATE TABLE IF NOT EXISTS outbox_event (
    event_id text PRIMARY KEY,
    event_type text NOT NULL,
    aggregate_type text,
    aggregate_id text,
    exchange_name text NOT NULL,
    routing_key text NOT NULL,
    payload_json jsonb NOT NULL,
    idempotency_key text,
    status text NOT NULL,
    retry_count integer NOT NULL DEFAULT 0,
    next_retry_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    published_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_outbox_event_idempotency
    ON outbox_event (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_event_dispatch
    ON outbox_event (status, next_retry_at, created_at);

CREATE TABLE IF NOT EXISTS inbox_event (
    event_id text NOT NULL,
    consumer_name text NOT NULL,
    status text NOT NULL,
    received_at timestamptz NOT NULL,
    processed_at timestamptz,
    last_error text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_inbox_event_status
    ON inbox_event (consumer_name, status, updated_at);
