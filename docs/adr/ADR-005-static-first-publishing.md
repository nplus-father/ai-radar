# ADR-005: Static-first publishing (git as the delivery mechanism)

- Status: Proposed (skeleton — flesh out during M1)
- Date: 2026-07-06

## Decision (summary)

All public output — digest markdown and hourly metrics snapshots — is committed
to the site repository and served by GitHub Pages. No runtime backend is
exposed. Content gets version history for free; the fixed-IP host needs zero
inbound ports. Pages deploy throttling on bursty pushes is handled by the
publisher's retry semantics.
