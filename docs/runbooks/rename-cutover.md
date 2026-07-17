# Runbook: rename `ai-radar` → `bookshelf-echo`

Full migration of the project identity: display brand, GitHub repos, ghcr
images, compose/container names, site URL path, Prometheus targets. Prepared as
local branches `rename/bookshelf-echo` in three repos (ai-radar,
ai-radar-site, nplus-static/nplus-infra); **nothing is pushed yet**.

## Scope decisions (what is deliberately NOT renamed)

- **`airadar` Postgres DB/user and RabbitMQ user** — live data identifiers.
  Renaming them is a data migration, not a rebrand. Kept as-is.
- **Kotlin package `wiki.nplus.airadar`** (pipeline) and
  `space.nplus.features.airadar` (backend) — internal namespaces, zero user
  impact, pure churn to change. Kept.
- **`docs/adr/*`** — historical decision records, kept as written.
- **nplus-backend `AiRadar*` code** — another session's WIP repo; the URL
  cutover is done via env vars (below), no code edit required.

Naming map: display `AI Radar → Bookshelf Echo`; slug `ai-radar → bookshelf-echo`,
`ai-radar-site → bookshelf-echo-site`; ghcr `ai-radar-* → bookshelf-echo-*`.

## Blast radius (functional couplings that MUST cut over together)

1. `container_name` (compose) ↔ `prometheus.yml` target hostnames — Prometheus
   scrapes containers by name. Rename both or metrics stop.
2. Site GitHub repo name ↔ GitHub Pages base path (`astro.config.mjs` `base`)
   ↔ `SITE` constants ↔ the LINE job's daily/essay URLs.
3. ghcr image names ↔ compose `image:` refs ↔ `deploy.yml`. New CI run must
   publish the new names before deploy pulls them.

## Cutover order (on the deploy host `nplus.space`, user `dev`)

Coordinate with the other Claude session on the deploy host first.

### 1. GitHub repo renames (web UI or `gh`)
- `nplus-father/ai-radar` → `bookshelf-echo`
- `nplus-father/ai-radar-site` → `bookshelf-echo-site`

GitHub keeps a redirect for **git remotes** (old clones keep working), but
**ghcr package names and the Pages URL do not auto-rename**.

### 2. Push the three branches, open/merge to main
```
# dev machine (this repo set)
git -C ~/workspace/ai-radar               push origin rename/bookshelf-echo
git -C ~/workspace/ai-radar-site          push origin rename/bookshelf-echo
git -C ~/workspace/.../nplus-infra        push origin rename/bookshelf-echo
```
Review diffs, merge each to `main`.

### 3. ghcr images — let CI rebuild
Merging ai-radar triggers `deploy.yml`, which builds
`ghcr.io/nplus-father/bookshelf-echo-{producers,enricher,matcher,digester,publisher}`.
Old `ai-radar-*` packages can be deleted later.

### 4. Site — GitHub Pages
- After the site repo rename + merge, Pages rebuilds at base
  `/bookshelf-echo-site/`. Custom domain `nplus.wiki` (CNAME in the site repo)
  is unchanged, so the new public root is
  **`https://nplus.wiki/bookshelf-echo-site/`**.
- **Old links break**: every `nplus.wiki/ai-radar-site/...` permalink 404s.
  Personal-scale, low traffic → acceptable. If a redirect is wanted, keep the
  old repo as a stub with a meta-refresh `index.html`. Not doing so is fine.

### 5. Deploy host — recreate the stack (⚠️ MIGRATE VOLUMES FIRST)

**Data-loss trap:** the compose `name:` changes `ai-radar → bookshelf-echo`.
Docker named volumes are prefixed by project name, so the live database and
queue live in `ai-radar_pg-data` / `ai-radar_rabbitmq-data`. A plain
`down` + `up` under the new project would create **empty** `bookshelf-echo_*`
volumes — every item/match/essay and all queued messages gone. The volumes
MUST be migrated (copied) before the new project starts.

```
cd ~/workspace
# 5a. stop the OLD project explicitly (name is still ai-radar on the host copy)
docker compose -p ai-radar -f ai-radar/docker-compose.yml down

# 5b. copy each named volume ai-radar_* -> bookshelf-echo_*
for v in pg-data rabbitmq-data; do
  docker volume create "bookshelf-echo_$v"
  docker run --rm -v "ai-radar_$v":/from -v "bookshelf-echo_$v":/to alpine \
    sh -c 'cd /from && cp -a . /to && echo "copied $v"'
done

# 5c. rename checkout dirs + remotes
mv ai-radar bookshelf-echo
mv ai-radar-site bookshelf-echo-site
cd bookshelf-echo
git remote set-url origin https://github.com/nplus-father/bookshelf-echo.git
git pull

# 5d. bring up the NEW project (uses the copied bookshelf-echo_* volumes)
docker compose up -d

# 5e. VERIFY before deleting anything
docker compose exec postgres psql -U airadar -d airadar \
  -c "SELECT count(*) FROM items;" -c "SELECT count(*) FROM matches;"
docker compose exec rabbitmq rabbitmqctl list_queues name messages | head
# only once counts match the pre-cutover numbers:
#   docker volume rm ai-radar_pg-data ai-radar_rabbitmq-data
```

Zero-copy alternative (skip 5b) if you'd rather keep the existing data in
place: pin the volumes to their current names in `docker-compose.yml` so the
new project reuses them —
`volumes: {pg-data: {name: ai-radar_pg-data, external: true}, rabbitmq-data: {name: ai-radar_rabbitmq-data, external: true}}`.
Leaves `ai-radar` in the volume names forever (cosmetic), no downtime for copy.

The compose default network changes `ai-radar_default → bookshelf-echo_default`
(internal, referenced as `default`; nothing external depends on the literal
name). `infra-shared-network` is external and unchanged.

### 6. Prometheus — reload
After merging nplus-infra and pulling on the host, reload Prometheus (or
recreate its container) so it scrapes the new `bookshelf-echo-*` targets.
**Grafana panels** querying `app="ai-radar"` must be updated to
`app="bookshelf-echo"`; there is a label discontinuity in metrics history
(old samples keep the old label — expected).

### 7. LINE daily push (nplus-backend) — one env var, no code change
Set ONE var on the backend's runtime env (`nplus-infra/backend.env`, host-only,
gitignored), then `docker compose up -d backend` to recreate:
```
AI_RADAR_DAILY_URL=https://nplus.wiki/bookshelf-echo-site/daily.json
```
Only `AI_RADAR_DAILY_URL` matters — the backend reads it via `Env.aiRadarDailyUrl`
(`Env.kt:89`). There is NO `AI_RADAR_ESSAY_URL` in the code; do not set it (it
was a phantom in an earlier draft of this runbook). The "read more" footer link
is `daily.pageUrl` from the payload itself (site-publisher writes it into
`daily.json`), not a backend env var — so this one var fully fixes the URL.

Why it matters: if `daily.json` 404s, `AiRadarDigestFetcher.fetch()` fails and
the ENTIRE card fails to send (not merely "broken when clicked") — the daily
LINE push silently stops until this is set.

The LINE card still reads "📡 AI Radar" — cosmetic; rebrand the display strings
(`AiRadarService`, `AiRadarFlexBuilder`) in a separate PR once that repo's WIP
settles.

## Rollback
Each repo's `main` can revert the rename merge; re-run CI to restore
`ai-radar-*` images; `git mv` the host dirs back; reset the two env vars. The
GitHub repo names can be renamed back (redirects make this safe).
