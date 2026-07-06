# ai-radar

A queue-based pipeline that collects AI-related news and papers from multiple
sources, digests them with an LLM, and publishes a daily static digest — plus a
public dashboard of the **system's own health metrics** (queue depth, latency,
DLQ, cost).

The interesting part is not the content; it is the reliability semantics:
at-least-once delivery with idempotent consumers, a TTL+DLX retry ladder, a
dead-letter queue with replay tooling, and an LLM cost circuit breaker whose
backlog lives in the queue. Every non-obvious decision has an ADR in
[`docs/adr/`](docs/adr/).

## Architecture

```
producers (hn, arxiv, gh-trending, blogs, reddit — each on its own cadence)
    │  publish ItemEnvelope, rk = item.<source>
    ▼
RabbitMQ 4.x ── ingest.q ─▶ enricher ── digest.q ─▶ digester ── publish.q ─▶ publisher
    │                        dedup +                 Claude API               markdown +
    │                        full text               (batched,                metrics JSON
    │                        → Postgres              budget-gated)            → git push
    │
    ├── retry.{30s,5m,1h}.q   (TTL + DLX ladder, no consumers — ADR-004)
    └── dlq.q                 (+ replay CLI)
                                                     site repo ─▶ GitHub Actions ─▶ Pages
```

State machine per item: `RECEIVED → ENRICHED → DIGESTED → PUBLISHED`
(terminal: `DUPLICATE`, `FAILED`). All transitions are idempotent (ADR-003).

Zero-inbound posture: the host only makes outbound calls (source APIs, LLM,
git push). Every compose port binds to `127.0.0.1`. The public face is a
statically built site.

## Modules

| Module       | Role                                                              |
|--------------|-------------------------------------------------------------------|
| `common`     | Wire contract (`ItemEnvelope`), broker topology, URL canonicalizer |
| `producers`  | Source pollers on independent cadences (coroutine scheduler)       |
| `enricher`   | Dedup (two layers) + full-text fetch                               |
| `digester`   | Batched LLM digestion, cost circuit breaker                        |
| `publisher`  | Renders digests + metrics snapshots, commits to the site repo      |

## Running

```bash
cp .env.example .env         # fill in passwords
docker compose up -d         # rabbitmq + postgres, migrations via flyway
./gradlew build              # compile + tests
```

## Status

| Milestone | Scope                                                        | State |
|-----------|--------------------------------------------------------------|-------|
| M0        | Skeleton, compose stack, schema, ADRs 001–003                | done  |
| M1        | First producer (`hn`) through the full pipeline, daily digest | done  |
| M2        | Retry ladder, DLQ + replay CLI, metrics, dashboard v1, budget breaker | — |
| M3        | Remaining producers, cross-source dedup, Batch API, weekly rollup | — |
| M4        | SLO doc, runbooks, 30-day live data, tech write-up            | —     |
