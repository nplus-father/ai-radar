#!/bin/sh
# Sync the pipeline's markdown output into the ai-radar-site checkout and push,
# so GitHub Actions rebuilds the public site. Runs as a compose sidecar; keeps
# the publisher itself unchanged (no git needed in the JRE image).
set -eu

git config --global --add safe.directory /repo
git config --global user.name "ai-radar-bot"
git config --global user.email "bot@nplus.wiki"

REPO_URL="https://x-access-token:${SITE_GIT_TOKEN}@github.com/nplus-father/ai-radar-site.git"
INTERVAL="${SYNC_INTERVAL_SECONDS:-300}"
echo "site-publisher: syncing /src -> /repo/content every ${INTERVAL}s"

while true; do
  mkdir -p /repo/content/daily /repo/content/weekly
  cp -r /src/daily/. /repo/content/daily/ 2>/dev/null || true
  cp -r /src/weekly/. /repo/content/weekly/ 2>/dev/null || true
  cd /repo
  git add -A
  if ! git diff --cached --quiet; then
    git commit -q -m "content: auto-publish $(date -u +%FT%TZ)"
    if git push -q "$REPO_URL" HEAD:main 2>/dev/null; then
      echo "pushed at $(date -u +%FT%TZ)"
    else
      echo "push FAILED at $(date -u +%FT%TZ)"
    fi
  fi
  sleep "$INTERVAL"
done
