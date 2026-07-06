-- Core schema. State machine and idempotency keys are the contract here:
-- consumers may see the same message more than once (at-least-once delivery,
-- ADR-003), so every side effect keys on items(source, external_id).

CREATE TABLE items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source        TEXT NOT NULL,
    external_id   TEXT NOT NULL,
    url           TEXT NOT NULL,
    canonical_url TEXT NOT NULL,
    title         TEXT NOT NULL,
    content_hash  TEXT NOT NULL,
    state         TEXT NOT NULL DEFAULT 'RECEIVED'
        CHECK (state IN ('RECEIVED', 'ENRICHED', 'DIGESTED', 'PUBLISHED', 'DUPLICATE', 'FAILED')),
    published_at  TIMESTAMPTZ,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_payload   JSONB,
    -- Cross-source duplicate: points at the item that got digested instead.
    duplicate_of  BIGINT REFERENCES items (id),
    UNIQUE (source, external_id)
);

-- Partial index: the pipeline only ever scans in-flight states.
CREATE INDEX items_state_active_idx ON items (state)
    WHERE state IN ('RECEIVED', 'ENRICHED', 'DIGESTED');
CREATE INDEX items_content_hash_idx ON items (content_hash);

CREATE TABLE item_contents (
    item_id        BIGINT PRIMARY KEY REFERENCES items (id),
    content_level  TEXT NOT NULL CHECK (content_level IN ('FULL', 'METADATA_ONLY')),
    extracted_text TEXT,
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE digests (
    item_id            BIGINT PRIMARY KEY REFERENCES items (id),
    summary_zh         TEXT NOT NULL,
    summary_en         TEXT NOT NULL,
    tags               JSONB NOT NULL,
    significance_score SMALLINT NOT NULL CHECK (significance_score BETWEEN 1 AND 5),
    category           TEXT NOT NULL,
    model              TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE llm_usage (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id       BIGINT REFERENCES items (id),
    purpose       TEXT NOT NULL CHECK (purpose IN ('DIGEST', 'WEEKLY_ROLLUP')),
    model         TEXT NOT NULL,
    input_tokens  INT NOT NULL,
    output_tokens INT NOT NULL,
    cost_usd      NUMERIC(10, 6) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX llm_usage_created_at_idx ON llm_usage (created_at);

CREATE TABLE publish_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind        TEXT NOT NULL CHECK (kind IN ('DAILY', 'WEEKLY', 'METRICS')),
    target_path TEXT NOT NULL,
    git_commit  TEXT,
    item_count  INT NOT NULL DEFAULT 0,
    status      TEXT NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE metrics_snapshots (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    snapshot    JSONB NOT NULL
);
