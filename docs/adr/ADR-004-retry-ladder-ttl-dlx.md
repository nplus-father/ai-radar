# ADR-004: Retry ladder via TTL + DLX (no delayed-message plugin)

- Status: Proposed (skeleton — flesh out during M2)
- Date: 2026-07-06

## Decision (summary)

Retryable failures are republished to per-origin tiered wait-queues
(`retry.<30s|5m|1h>.<origin queue>`) that have no consumers, only a message TTL
and a dead-letter policy routing back to the origin queue via the default
exchange (`x-dead-letter-routing-key = <origin>`). Per-origin queues cost a few
extra declarations but keep the return path declarative — no custom exchange,
no header-based re-routing. The attempt count travels in the `x-retry-count`
header; after 3 attempts, or on any non-retryable error (parse failure,
non-429 4xx, schema violation), the message goes to `dlq.q`. Budget exhaustion
is special-cased: it re-parks in the longest tier without consuming an attempt.
Chosen over the delayed-message plugin to avoid a plugin dependency and keep
the topology declarative. DLQ comes with a `replay` CLI and a runbook (M2).
