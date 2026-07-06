# ADR-001: Why a message broker at all

- Status: Accepted
- Date: 2026-07-06

## Context

ai-radar collects AI-related items from ~5 heterogeneous sources, digests them
with an LLM, and publishes a daily static digest. The honest first question any
reviewer will ask: **couldn't a GitHub Actions cron do this?**

Yes — for a single low-frequency source with no delivery guarantees, it could,
and that would be the right call. This ADR records why this system is
deliberately built around a broker, and where the line is.

## Decision drivers

1. **Producers run at different cadences** (hourly HN, daily arXiv, 4-hourly
   RSS…) and burst independently; downstream stages should consume at their own
   pace. A buffer between "collect" and "process" is the natural shape.
2. **The LLM stage has real constraints**: rate limits (429s), per-day cost
   budget, and batch economics. When the daily budget circuit-breaker trips
   (see design doc §3.4), in-flight items must wait somewhere durable until the
   next day — the queue *is* that backlog.
3. **Reliability semantics are the point of the demo**: at-least-once delivery,
   idempotent consumers, a retry ladder, a DLQ with replay tooling. These only
   exist to be designed if there is a broker.

## Considered options

- **GitHub Actions cron + direct calls** — simplest; fine for one source; no
  durable backlog, retries limited to job-level re-runs, no backpressure.
- **In-process queues (channels/coroutines)** — no durability across restarts;
  the cost-circuit-breaker backlog would live in memory.
- **Message broker (chosen)** — durable buffering, per-message retry semantics,
  observable depth/lag, natural producer/consumer decoupling.

## Decision

Use a message broker between every pipeline stage
(producers → enrich → digest → publish). Broker selection is ADR-002.

## Consequences

- One more stateful service to operate (mitigated: single docker-compose host,
  management UI bound to localhost only).
- The system gains a truthful answer to "why a queue": multi-cadence producers,
  budget-gated consumption, and demonstrable reliability semantics.
- We explicitly acknowledge the over-engineering line: at this traffic volume a
  cron would "work". Knowing that line — and building past it only for
  articulated reasons — is part of what this repository demonstrates.
