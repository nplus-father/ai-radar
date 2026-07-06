# ADR-002: RabbitMQ over Redis Streams and SQS

- Status: Accepted
- Date: 2026-07-06

## Context

ADR-001 decided on a broker. Candidates: RabbitMQ, Redis Streams, AWS SQS,
Kafka.

## Considered options

### RabbitMQ 4.x (chosen)

- Native TTL + dead-letter-exchange primitives → the retry ladder (ADR-004)
  needs zero plugins and zero custom code.
- Quorum queues give durable, replicable semantics (single node here, but the
  declaration is the same).
- Management API exposes queue depth / rates → feeds the public dashboard.
- Operational familiarity: production experience running and debugging
  RabbitMQ (including a 4.x upgrade breaking transient-queue consumers)
  transfers directly, and this demo doubles as a reference implementation of
  the semantics that incident taught.

### Redis Streams

- Lighter footprint; consumer groups + PEL cover at-least-once.
- But retry/DLQ must be hand-rolled (XAUTOCLAIM loops, custom parking lists),
  which buries exactly the semantics this project wants to make explicit.

### AWS SQS (+ Lambda)

- Zero ops, built-in DLQ and delay — genuinely the pragmatic choice for a
  product.
- Rejected here because it outsources the interesting parts: topology design,
  backpressure tuning, broker operations. Also ties a personal demo to a cloud
  account.

### Kafka

- Wrong fit: log-oriented, heavyweight for a single-host pipeline, and its
  strengths (replay, partitioned ordering, high throughput) aren't the
  constraints here.

## Decision

RabbitMQ 4.x, single node, quorum queues, topology declared in code
(`RabbitTopology` in `:common`).

## Consequences

- Broker upgrade discipline required (pin minor version in compose; upgrades
  get a runbook entry — semantics changed across 4.x before).
- Management UI/API stays localhost-bound; the public dashboard consumes
  metrics snapshots committed to the site repo instead.
