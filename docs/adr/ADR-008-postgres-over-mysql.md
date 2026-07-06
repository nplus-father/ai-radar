# ADR-008: PostgreSQL over reusing the host's existing MySQL

- Status: Accepted
- Date: 2026-07-06

## Decision (summary)

The host already runs MySQL for another service; ai-radar deliberately runs its
own PostgreSQL 17 container instead. Reasons: (1) the workload fits Postgres
strengths — JSONB for raw payloads and LLM output, `INSERT … ON CONFLICT` as
the idempotency primitive (MySQL's `ON DUPLICATE KEY` has different semantics,
e.g. auto-increment burn), partial indexes on in-flight states; (2) isolation —
a compose-internal DB shares nothing with the other service; (3) breadth —
operating both engines side by side, and being able to articulate the
differences, is itself valuable. Trade-off accepted: two database engines on
one host means two backup/upgrade stories.
