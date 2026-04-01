#!/usr/bin/env bash
# Run Renovate locally against this repo using Docker.
# Requires: Docker, a GITHUB_TOKEN with Contents R/W + Pull requests R/W
#
# Usage:
#   export GITHUB_TOKEN=<your-token>
#   ./renovate-local.sh
#
# To do a dry-run (no PRs created):
#   DRY_RUN=true ./renovate-local.sh

set -euo pipefail

REPO="rygel/quicklaunch"
TOKEN="${RENOVATE_TOKEN:-${GITHUB_TOKEN:-}}"
DRY_RUN="${DRY_RUN:-false}"

if [[ -z "$TOKEN" ]]; then
  echo "Error: set RENOVATE_TOKEN or GITHUB_TOKEN before running." >&2
  exit 1
fi

LOG_LEVEL="${LOG_LEVEL:-info}"
if [[ "$DRY_RUN" == "true" ]]; then
  echo "Dry-run mode — no PRs will be created."
  DRY_RUN_FLAG="--dry-run=full"
else
  DRY_RUN_FLAG=""
fi

docker run --rm \
  -e RENOVATE_TOKEN="$TOKEN" \
  -e LOG_LEVEL="$LOG_LEVEL" \
  ghcr.io/renovatebot/renovate:latest \
  $DRY_RUN_FLAG \
  --platform=github \
  "$REPO"
