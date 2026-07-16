-- M5: daily selection (curator). The shortlist is the pool of items judged
-- worth a deep, book-informed commentary — deliberately OUTSIDE the items
-- state machine (ADR-009): an item's linear lifecycle (… → DIGESTED →
-- PUBLISHED) continues untouched whether or not it is shortlisted, so the
-- publisher never has to know this table exists.

CREATE TABLE shortlist (
    item_id        BIGINT PRIMARY KEY REFERENCES items (id),
    rationale      TEXT NOT NULL,
    model          TEXT NOT NULL,
    shortlisted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set when a daily insight (M6) consumes this pick; NULL = still in the pool.
    composed_at    TIMESTAMPTZ
);

-- One selection run per UTC day — the curator's idempotency key. The latest
-- created_at is also the low-water mark for the next run's candidate window,
-- so a day skipped (budget exhausted, process down) folds into the next run
-- instead of losing its digests.
CREATE TABLE selection_runs (
    day             DATE PRIMARY KEY,
    model           TEXT NOT NULL,
    candidate_count INT NOT NULL,
    picked_count    INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE llm_usage DROP CONSTRAINT llm_usage_purpose_check;
ALTER TABLE llm_usage ADD CONSTRAINT llm_usage_purpose_check
    CHECK (purpose IN ('DIGEST', 'WEEKLY_ROLLUP', 'SELECT'));
