# Runbook: host deployment

The whole stack runs under Docker Compose at `~/workspace/ai-radar`: infra
(RabbitMQ + Postgres + Flyway) plus the five pipeline apps
(producers/enricher/matcher/digester/publisher) as Jib-built images
`ghcr.io/nplus-father/ai-radar-*`. Host ports bind `127.0.0.1` only (ADR-006);
each app's metrics port is exposed only on the internal Docker network and
scraped by the shared Prometheus — never published to the host.

## First-time setup

```bash
cd ~/workspace/ai-radar
cp .env.example .env      # fill RABBITMQ_PASSWORD, POSTGRES_PASSWORD, GEMINI_API_KEY, SITE_GIT_TOKEN
docker network create infra-shared-network 2>/dev/null || true   # normally created by the nplus-infra stack
docker login ghcr.io      # if the images are private
docker compose up -d      # infra + flyway migrate + the 4 apps
```

The apps join two networks: the ai-radar default (to reach `postgres`/`rabbitmq`
by name) and the external `infra-shared-network` (so infra-prometheus can scrape
them). In-container overrides `DATABASE_URL`, `RABBITMQ_HOST`, `RABBITMQ_PORT`,
`RABBITMQ_MGMT_PORT`, `METRICS_BIND` are set in compose; everything else comes
from `.env`. The `*_PORT` values in `.env` are host bindings only — remapping one
to dodge a host conflict must not (and now does not) change in-container
addressing.

## Upgrade / deploy

CI (`.github/workflows/deploy.yml`) builds & pushes all four images with Jib on
every push to `main`, then deploys over SSH. Manually:

```bash
cd ~/workspace/ai-radar
git pull
docker compose pull producers enricher matcher digester publisher
docker compose up -d
```

Schema changes are applied by the flyway compose service on `up`.

### news-echo Phase 2 rollout (one-time, ADR-010)

1. `.env` needs `LIBRARY_SECRET` (same value as the bridge's `BRIDGE_SECRET`
   in nplus-infra `bridge.env`); `LIBRARY_URL` defaults to
   `http://library-bridge:7788` on `infra-shared-network`.
2. The library-bridge image must include the `/search`+`/chapter` routes
   (book-library-hub commit `37cc39b` or later) — rebuild/redeploy it first.
3. Items already ENRICHED have messages parked on digest.q; the digester
   now no-ops them (guard is MATCHED). Run `ops redrive --apply` once after
   the deploy to re-route them through match.q.

## Build images locally (no registry)

```bash
./gradlew jibDockerBuild   # builds all five images straight into the local Docker daemon
docker compose up -d
```

## Health checks

```bash
docker compose ps
docker logs -f ai-radar-digester
docker exec ai-radar-rabbitmq rabbitmqctl list_queues name messages consumers
docker compose exec postgres psql -U airadar -d airadar -c "SELECT state, count(*) FROM items GROUP BY state"
```

Monitoring: Prometheus scrapes `ai-radar-{producers,enricher,matcher,digester,publisher}:910x`
and `ai-radar-rabbitmq:15692` over `infra-shared-network`; the **AI Radar
Pipeline** Grafana dashboard shows queue depth, digest rate, LLM cost and DLQ.
All containers are visible in Portainer.

The ops CLI runs from the host against the published 127.0.0.1 ports:

```bash
./gradlew :ops:installDist
set -a; source .env; set +a
./ops/build/install/ops/bin/ops dlq list
```

## Items stranded mid-pipeline

Every stage commits its state transition and only then publishes the message for
the next queue, so a process that dies in between leaves the row advanced with
nothing to drive it. Symptom: `items` in `ENRICHED` outnumber the messages across
`digest.q` + `retry.*.digest.q` (both are on the dashboard snapshot), and the
count never drains.

```bash
./ops/build/install/ops/bin/ops redrive           # counts only
./ops/build/install/ops/bin/ops redrive --apply   # re-queue them
```

Re-queuing the whole state is safe rather than surgical: nothing in the DB
distinguishes a stranded item from one legitimately waiting its turn under the
daily cap, and ADR-003's idempotent consumers mean a duplicate for an
already-queued item costs one trip round the retry ladder and then no-ops. No
duplicate LLM spend.

## Rebuilding a day's digest page

`ops republish <YYYY-MM-DD>` re-emits one of that UTC day's items onto
`publish.q`; the publisher then regenerates the page from Postgres. Regeneration
reads the whole day and is idempotent, so it is safe to re-run.

```bash
./ops/build/install/ops/bin/ops republish 2026-07-15
```

Use it whenever a page is missing or stale on the site but the digests exist in
the DB — the DB is the source of truth for page content.

> **One-off after the digest-day fix.** The publisher used to key each page on
> `items.received_at` while filling it from `digests.created_at`. Those clocks
> only lined up by luck, so each day's digests were published a day late, onto
> whichever page the triggering item happened to arrive on. Pages are now keyed
> on `digests.created_at`. The day still in flight when that fix deploys never
> gets its page written — rebuild that one day by hand, once:
>
> ```bash
> ./ops/build/install/ops/bin/ops republish <in-flight day>   # e.g. the deploy day
> ```

## Broker upgrade discipline (ADR-002)

RabbitMQ minor upgrades changed queue semantics before (4.3 vs transient
non-exclusive queues). Before bumping the image tag: read the release notes for
queue-declaration changes, upgrade in a throwaway compose project first, and
only then the live one.
