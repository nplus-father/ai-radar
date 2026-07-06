# ADR-004: Retry ladder via TTL + DLX (no delayed-message plugin)

- Status: Proposed (skeleton — flesh out during M2)
- Date: 2026-07-06

## Decision (summary)

Retryable failures are republished to tiered queues (`retry.30s.q`,
`retry.5m.q`, `retry.1h.q`) that have no consumers, only a message TTL and a
dead-letter exchange pointing back at `ingest.x`. The attempt count travels in
the `x-retry-count` header; after 3 attempts, or on any non-retryable error
(parse failure, non-429 4xx, schema violation), the message goes to `dlq.q`.
Chosen over the delayed-message plugin to avoid a plugin dependency and keep
the topology declarative. DLQ comes with a `replay` CLI and a runbook.
