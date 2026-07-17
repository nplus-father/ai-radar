# Next steps

Snapshot 2026-07-17. Ordered by priority. Cross-repo facts live in Claude's
project memory (`news-echo-status`); this file is the human-readable backlog.

## P0 — Diagnose why no essay ever publishes (the original product question)

The design faithfully implements the intended 4-step vision — valuable channels
→ filter to the truly valuable → resonate against the book library → deep
book-informed essay — but the site's **書櫃評析 section is empty**: no essay has
ever been published. Before optimizing signal quality, find out which case
we're in:

- **(A) the LLM relevance judge rejects every candidate, every day** → the
  system is working as designed (寧缺勿濫; world-news × a broad shelf is mostly
  coincidence). Then P1 (theme-index) is the right lever.
- **(B) the pipeline is stuck before the essay stage** → a bug to fix first.

How (we have prod read access):
```
ssh nplus.space "docker exec -i ai-radar-postgres-1 psql -U airadar -d airadar"
```
Check: is `selection_runs` firing daily? is `shortlist` being populated (and are
picks `composed_at`)? are there judge verdicts in `llm_usage` (by `purpose`)?
any rows in `essays` at all? are items reaching `DIGESTED`/`PUBLISHED` or piling
in `STALE`/`NO_RESONANCE`?
Refs: ADR-009 (curator/shortlist), ADR-010 + amendment (resonance gate, judge).

## P1 — Theme-index experiment (only if P0 = A)

Test whether an isolated theme vector discriminates genuine vs coincidence.
Everything is staged in `docs/experiments/theme-index/` (frozen 30-item news
sample + protocol). **Blocker: hand-label the 30** (`label-sheet.tsv`), then
build `book_theme_vectors` in book-library-hub and score vs the 0.20 baseline.
~$0.25 voyage, one-time. See that folder's README.

## P1 — Finish the bookshelf-echo rename cutover

GitHub repos already renamed: `ai-radar → bookshelf-echo`,
`ai-radar-site → bookshelf-echo-site`. Chosen approach: **full runtime
migration, done manually** (not a plain `git merge`). Full runbook:
`docs/runbooks/rename-cutover.md`.

- [x] **Site** — `rename/bookshelf-echo` merged to `main` and pushed
      (2026-07-17). GitHub Actions rebuilds Pages at `/bookshelf-echo-site/`.
- [ ] **Pipeline** — do NOT just merge to main: the branch changes the compose
      project name, so a plain deploy would create empty `bookshelf-echo_*`
      volumes and **wipe the Postgres DB + queue**. Must be a hand-run cutover
      on the deploy host with **volume migration** (`ai-radar_{pg,rabbitmq}-data
      → bookshelf-echo_*`, verify row counts, then merge + `docker compose up`).
      See runbook §5.
- [ ] Merge `Andrewnplus/nplus-infra` (prometheus targets) in lockstep with the
      pipeline container rename; reload Prometheus + fix Grafana `app=` label.
- [ ] nplus-backend LINE job: set env `AI_RADAR_DAILY_URL` /
      `AI_RADAR_ESSAY_URL` → `.../bookshelf-echo-site/...` (no code change).
- [ ] Optional later: rebrand the LINE card "📡 AI Radar" heading once that
      repo's WIP settles. Old `nplus.wiki/ai-radar-site/...` links will 404.

Deliberately NOT renamed: `airadar` DB/RabbitMQ identifiers, Kotlin package
`wiki.nplus.airadar`, `docs/adr/*`. (Compose project/volume/container names ARE
renamed under the full-migration choice — hence the volume migration above.)

## P2 — Product / positioning

- Source is now BBC world news (gh-trending dropped). Decide whether
  world-news × a broad shelf is the intended product, or whether the channels
  should be narrowed to raise the base rate of genuine resonance. This directly
  affects how often an essay can honestly publish (P0/P1).
