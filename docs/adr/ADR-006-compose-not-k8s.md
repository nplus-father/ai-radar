# ADR-006: docker-compose on a single host, not Kubernetes

- Status: Proposed (skeleton — flesh out during M2)
- Date: 2026-07-06

## Decision (summary)

The whole stack runs as one docker-compose file on a fixed-IP home server,
every port bound to 127.0.0.1. Kubernetes on a single-node hobby deployment
adds operational surface without any of its benefits — knowing when *not* to
use it is the point. The queue-depth alert signal documented in M2 is exactly
what would drive KEDA-style autoscaling if this ever ran on a cluster.
