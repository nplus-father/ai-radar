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

### 5. Deploy host — recreate the stack
```
cd ~/workspace
mv ai-radar bookshelf-echo            # rename checkout dir
mv ai-radar-site bookshelf-echo-site
cd bookshelf-echo
git remote set-url origin https://github.com/nplus-father/bookshelf-echo.git
git pull
# compose project + container + image names all changed → full recreate
docker compose down                   # tears down old ai-radar-* containers
docker compose up -d                  # brings up bookshelf-echo-* containers
```
The compose default network changes `ai-radar_default → bookshelf-echo_default`
(internal, referenced as `default`; nothing external depends on the literal
name). `infra-shared-network` is external and unchanged.

### 6. Prometheus — reload
After merging nplus-infra and pulling on the host, reload Prometheus (or
recreate its container) so it scrapes the new `bookshelf-echo-*` targets.
**Grafana panels** querying `app="ai-radar"` must be updated to
`app="bookshelf-echo"`; there is a label discontinuity in metrics history
(old samples keep the old label — expected).

### 7. LINE daily push (nplus-backend) — env vars only, no code change
Set on the backend's runtime env (the `AiRadar*` code reads these; the
hardcoded values are only fallbacks):
```
AI_RADAR_DAILY_URL=https://nplus.wiki/bookshelf-echo-site/daily.json
AI_RADAR_ESSAY_URL=https://nplus.wiki/bookshelf-echo-site/essay.json
```
Restart the backend. The LINE card still reads "📡 AI Radar" — cosmetic;
rebrand the display strings (`AiRadarService`, `AiRadarFlexBuilder`) in a
separate PR once that repo's WIP settles.

## Rollback
Each repo's `main` can revert the rename merge; re-run CI to restore
`ai-radar-*` images; `git mv` the host dirs back; reset the two env vars. The
GitHub repo names can be renamed back (redirects make this safe).
