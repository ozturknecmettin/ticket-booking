-- ── Domain tables ─────────────────────────────────────────────────────────────

CREATE TABLE bookings (
    booking_id   VARCHAR(36)    PRIMARY KEY,
    show_id      VARCHAR(36)    NOT NULL,
    customer_id  VARCHAR(36)    NOT NULL,
    seats        TEXT           NOT NULL,
    total_amount NUMERIC(10,2)  NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    ticket_number VARCHAR(100),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    version      BIGINT
);

-- ── Axon dead-letter queue ─────────────────────────────────────────────────────

CREATE TABLE dead_letter_entry (
    dead_letter_id          VARCHAR(255) NOT NULL,
    cause_message           TEXT,
    cause_type              VARCHAR(255),
    diagnostics             TEXT,
    enqueued_at             TIMESTAMP(6) NOT NULL,
    last_touched            TIMESTAMP(6),
    aggregate_identifier    VARCHAR(255),
    event_identifier        VARCHAR(255),
    message_type            VARCHAR(255),
    meta_data               BYTEA,
    payload                 BYTEA        NOT NULL,
    payload_revision        VARCHAR(255),
    payload_type            VARCHAR(255) NOT NULL,
    sequence_number         BIGINT,
    time_stamp              VARCHAR(255) NOT NULL,
    token                   BYTEA,
    token_type              VARCHAR(255),
    type                    VARCHAR(255),
    processing_group        VARCHAR(255) NOT NULL,
    processing_started      TIMESTAMP(6),
    sequence_identifier     VARCHAR(255) NOT NULL,
    sequence_index          BIGINT       NOT NULL,
    PRIMARY KEY (dead_letter_id)
);

CREATE INDEX idx_dlq_processing_group ON dead_letter_entry (processing_group);
CREATE INDEX idx_dlq_sequence         ON dead_letter_entry (processing_group, sequence_identifier);

-- ── Axon token store ───────────────────────────────────────────────────────────

CREATE TABLE token_entry (
    processor_name  VARCHAR(255) NOT NULL,
    segment         INT          NOT NULL,
    owner           VARCHAR(255),
    timestamp       VARCHAR(255) NOT NULL,
    token           BYTEA,
    token_type      VARCHAR(255),
    PRIMARY KEY (processor_name, segment)
);

-- ── Axon event store ───────────────────────────────────────────────────────────

CREATE TABLE domain_event_entry (
    global_index            BIGSERIAL    NOT NULL,
    aggregate_identifier    VARCHAR(255) NOT NULL,
    sequence_number         BIGINT       NOT NULL,
    type                    VARCHAR(255),
    event_identifier        VARCHAR(255) NOT NULL UNIQUE,
    meta_data               BYTEA,
    payload                 BYTEA        NOT NULL,
    payload_revision        VARCHAR(255),
    payload_type            VARCHAR(255) NOT NULL,
    time_stamp              VARCHAR(255) NOT NULL,
    PRIMARY KEY (global_index),
    UNIQUE (aggregate_identifier, sequence_number)
);

CREATE TABLE snapshot_event_entry (
    aggregate_identifier    VARCHAR(255) NOT NULL,
    sequence_number         BIGINT       NOT NULL,
    type                    VARCHAR(255) NOT NULL,
    event_identifier        VARCHAR(255) NOT NULL UNIQUE,
    meta_data               BYTEA,
    payload                 BYTEA        NOT NULL,
    payload_revision        VARCHAR(255),
    payload_type            VARCHAR(255) NOT NULL,
    time_stamp              VARCHAR(255) NOT NULL,
    PRIMARY KEY (aggregate_identifier, sequence_number)
);
