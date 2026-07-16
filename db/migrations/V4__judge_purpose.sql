-- The essay relevance judge (news-echo): a cheap LLM call between the curator
-- and the essayist that asks "does this bookshelf evidence genuinely
-- illuminate this news, or is it a keyword coincidence?". Added after the
-- first live calibration showed absolute vector distance measures library
-- density, not relatedness — the gate stays as a coarse trash filter, the
-- judge is the real gatekeeper.
ALTER TABLE llm_usage DROP CONSTRAINT llm_usage_purpose_check;
ALTER TABLE llm_usage ADD CONSTRAINT llm_usage_purpose_check
    CHECK (purpose IN ('DIGEST', 'WEEKLY_ROLLUP', 'SELECT', 'ESSAY', 'JUDGE'));
