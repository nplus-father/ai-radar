# ADR-009: Daily selection is a DB batch job, not a queue stage

- Status: Accepted
- Date: 2026-07-16

## Context

M5 adds a second judgment tier on top of the per-item digest: once a day, a
stronger model ranks the day's digests *relative to each other* and shortlists
the few items worth a deep, book-informed commentary (M6). Conceptually this is
"queue B" — the pool of items that passed a higher bar than the digest score.

## Decision

The shortlist is a Postgres table plus a once-per-UTC-day curator loop inside
the digester process — **not** a new state on `items`, and **not** a new
RabbitMQ queue/consumer.

1. **The unit of work is the day's set, not an item.** Relative ranking needs
   every candidate in one prompt; a per-item queue message cannot carry "the
   whole day". The natural idempotency key is the day (`selection_runs.day`),
   and the candidate window starts at the previous run's timestamp, so a missed
   day (budget spent, process down) folds into the next run instead of losing
   its digests.
2. **The item state machine stays linear.** `… → DIGESTED → PUBLISHED` is
   unchanged whether or not an item is shortlisted; the publisher never learns
   this table exists. Shortlisting is an orthogonal judgment, so it lives in an
   orthogonal table (`shortlist`, with `composed_at` marking consumption by M6).
3. **One process spends LLM money.** The daily budget check is read-then-act
   with no DB-level guard; it is race-free only because a single digester
   serializes it. The curator therefore runs as a tick loop in that same
   process — a separate "curator" service would reintroduce the race.

## Consequences

- Queues keep doing what they are good at (per-item at-least-once with the
  retry ladder); daily batch judgment lives where set-valued, run-once-per-day
  semantics are natural (Postgres).
- Model tiers are per-instance config of the same `LlmClient` interface:
  cheap `GEMINI_MODEL` per item, stronger `SELECT_MODEL` once a day, each with
  its own per-Mtok rates in the shared cost ledger (`llm_usage.purpose`).
- Crash between recording usage and recording the run re-runs the selection
  next tick: shortlist inserts are `ON CONFLICT DO NOTHING`, so the worst case
  is one duplicate LLM call, never duplicate picks.
- Observability: `selection_runs` is the audit trail, `ops shortlist` shows the
  live pool, and the metrics snapshot exports `shortlist.pendingCount`.
