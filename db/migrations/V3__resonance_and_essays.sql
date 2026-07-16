-- Phase 2 (news-echo): the resonance gate and the daily essay.
--
-- matcher sits between enricher and digester: it embeds the news against the
-- book library (via library-bridge /search) BEFORE any LLM spend. Items whose
-- bookshelf has nothing to say park in NO_RESONANCE, terminal, at zero LLM
-- cost (ADR-010). The rest carry their match evidence forward in `matches`.

ALTER TABLE items DROP CONSTRAINT items_state_check;
ALTER TABLE items ADD CONSTRAINT items_state_check
    CHECK (state IN ('RECEIVED', 'ENRICHED', 'MATCHED', 'DIGESTED', 'PUBLISHED',
                     'DUPLICATE', 'FAILED', 'NO_RESONANCE'));

DROP INDEX items_state_active_idx;
CREATE INDEX items_state_active_idx ON items (state)
    WHERE state IN ('RECEIVED', 'ENRICHED', 'MATCHED', 'DIGESTED');

CREATE TABLE matches (
    item_id           BIGINT PRIMARY KEY REFERENCES items (id),
    -- Raw cosine distance of the nearest book vector. The gate and all
    -- thresholds key on this; RRF scores are rank-only (spike report).
    top_book_distance NUMERIC(6, 4) NOT NULL,
    books             JSONB NOT NULL,
    passages          JSONB NOT NULL,
    matched_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- At most one essay per UTC day ("有共鳴才寫" — days without one are legal).
CREATE TABLE essays (
    day        DATE PRIMARY KEY,
    item_id    BIGINT NOT NULL REFERENCES items (id),
    title      TEXT NOT NULL,
    essay_md   TEXT NOT NULL,
    books      JSONB NOT NULL,
    model      TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE llm_usage DROP CONSTRAINT llm_usage_purpose_check;
ALTER TABLE llm_usage ADD CONSTRAINT llm_usage_purpose_check
    CHECK (purpose IN ('DIGEST', 'WEEKLY_ROLLUP', 'SELECT', 'ESSAY'));

ALTER TABLE publish_log DROP CONSTRAINT publish_log_kind_check;
ALTER TABLE publish_log ADD CONSTRAINT publish_log_kind_check
    CHECK (kind IN ('DAILY', 'WEEKLY', 'METRICS', 'ESSAY'));
