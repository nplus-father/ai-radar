# ADR-007: LLM Batch API on a daily cadence, not realtime digestion

- Status: Proposed (skeleton — flesh out during M3)
- Date: 2026-07-06

## Decision (summary)

Items are digested in batches (20 items or the daily cutoff, whichever first)
via the Claude Batch API: half the token price, and the product is a *daily*
digest anyway, so latency is not a constraint. A per-day budget circuit
breaker halts digestion when spent; the backlog waits in the queue (see
ADR-001 driver #2).
