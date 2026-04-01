-- ── Domain tables ─────────────────────────────────────────────────────────────

CREATE TABLE shows (
    show_id        VARCHAR(36)    PRIMARY KEY,
    title          VARCHAR(255)   NOT NULL,
    venue          VARCHAR(255)   NOT NULL,
    event_date     TIMESTAMP,
    category       VARCHAR(100),
    artist_name    VARCHAR(255),
    description    TEXT,
    duration_minutes INT,
    total_seats    INT            NOT NULL,
    available_seats INT           NOT NULL DEFAULT 0,
    ticket_price   NUMERIC(10,2),
    price_zones    TEXT,
    status         VARCHAR(20)    NOT NULL,
    version        BIGINT
);

CREATE INDEX idx_shows_status ON shows (status);

CREATE TABLE show_reserved_seats (
    show_id     VARCHAR(36)  NOT NULL REFERENCES shows(show_id),
    seat_number VARCHAR(20)  NOT NULL,
    PRIMARY KEY (show_id, seat_number)
);

CREATE INDEX idx_reserved_seats_show ON show_reserved_seats (show_id);

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

-- ── Axon saga store ────────────────────────────────────────────────────────────

CREATE TABLE saga_entry (
    saga_id         VARCHAR(255) NOT NULL,
    revision        VARCHAR(255),
    saga_type       VARCHAR(255),
    serialized_saga BYTEA,
    PRIMARY KEY (saga_id)
);

CREATE SEQUENCE IF NOT EXISTS association_value_entry_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE association_value_entry (
    id              BIGINT       NOT NULL DEFAULT nextval('association_value_entry_seq'),
    association_key VARCHAR(255) NOT NULL,
    association_value VARCHAR(255),
    saga_id         VARCHAR(255),
    saga_type       VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE INDEX idx_assoc_saga_id   ON association_value_entry (saga_id);
CREATE INDEX idx_assoc_key_value ON association_value_entry (association_key, association_value);

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
