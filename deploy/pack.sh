#!/usr/bin/env bash
#
# pack.sh - One-click pack the dev environment for migration to a test server.
#
# What is packed (NO business data, per design):
#   1. N4-monitor code repo + root .env (compose reads it for interpolation)
#   2. N4-monitor self-built images (backend/frontend/flink) via docker save
#   3. Middleware compose definitions (.yaml/.yml/.env) under /opt - so the test
#      server can recreate the same middleware topology with docker compose up
#      (it pulls official images itself; NO data volumes are copied).
#
# Output: a single tarball <name>.tar.gz in the current directory.
#
# Usage:
#   sudo ./pack.sh [output-name]
#   (run on the dev server; sudo may be needed for docker / file perms)
#
set -euo pipefail

# ---- Config ---------------------------------------------------------------
DEV_ROOT="/opt"                    # parent dir of all compose projects on dev
PROJECTS=("emqx" "fuxa" "kafka" "mysql" "redis" "tdengine" "N4-monitor")
# Gitea is intentionally excluded (auth/credentials live there); add it only if
# the test env also needs the git server itself.
# N4-monitor self-built images to export. Keep in sync with docker-compose.yml.
N4_IMAGES=("n4-monitor-backend:latest" "n4-monitor-frontend:latest" "n4-monitor-flink-job:latest")

OUT_NAME="${1:-n4-monitor-pack-$(date +%Y%m%d-%H%M%S)}"
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT

echo "==> Packing to: ${OUT_NAME}.tar.gz"

# ---- 1. N4-monitor repo (code + .env) --------------------------------------
if [ ! -d "${DEV_ROOT}/N4-monitor" ]; then
  echo "ERROR: ${DEV_ROOT}/N4-monitor not found" >&2
  exit 1
fi

echo "==> Rebuilding N4-monitor images (ensures the pack carries the latest build)..."
(
  cd "${DEV_ROOT}/N4-monitor"
  # Only build services that actually exist; ignore missing profile services.
  docker compose build n4-monitor-backend n4-monitor-frontend 2>/dev/null \
    || docker compose build 2>/dev/null \
    || echo "WARN: compose build failed; exporting whatever images exist"
)

echo "==> Copying N4-monitor repo (git-tracked files + .env)..."
mkdir -p "${STAGE_DIR}/N4-monitor"
cd "${DEV_ROOT}/N4-monitor"
git ls-files -z | xargs -0 -I{} cp --parents "{}" "${STAGE_DIR}/N4-monitor/" 2>/dev/null || true
# .env may be untracked (or tracked); copy it explicitly.
[ -f .env ] && cp .env "${STAGE_DIR}/N4-monitor/.env" || echo "WARN: no .env in N4-monitor"
# .env.example for reference
[ -f .env.example ] && cp .env.example "${STAGE_DIR}/N4-monitor/.env.example" || true

# ---- 2. N4-monitor self-built images ---------------------------------------
echo "==> Exporting N4-monitor images via docker save..."
mkdir -p "${STAGE_DIR}/images"
for img in "${N4_IMAGES[@]}"; do
  if docker image inspect "$img" >/dev/null 2>&1; then
    echo "    - ${img}"
    docker save "$img" -o "${STAGE_DIR}/images/$(echo "$img" | tr '/:' '__').tar"
  else
    echo "    - SKIP ${img} (not present)"
  fi
done

# ---- 3. Middleware compose definitions (no data) ----------------------------
echo "==> Copying middleware compose definitions..."
for p in "${PROJECTS[@]}"; do
  [ "$p" = "N4-monitor" ] && continue
  if [ -d "${DEV_ROOT}/${p}" ]; then
    mkdir -p "${STAGE_DIR}/middleware/${p}"
    # Copy compose yaml(s) and env files only; skip data dirs/volumes.
    find "${DEV_ROOT}/${p}" -maxdepth 2 \
      \( -name '*.yaml' -o -name '*.yml' -o -name '.env' -o -name '*.env' \) \
      -type f -exec cp --parents {} "${STAGE_DIR}/middleware/" \; 2>/dev/null || true
    # Record the directory layout for reference.
    echo "${DEV_ROOT}/${p}" > "${STAGE_DIR}/middleware/${p}/SOURCE_PATH.txt"
    echo "    - ${p} (compose definitions)"
  else
    echo "    - SKIP ${p} (not found)"
  fi
done

# ---- 4. Assemble tarball ---------------------------------------------------
echo "==> Creating ${OUT_NAME}.tar.gz ..."
tar -czf "${OUT_NAME}.tar.gz" -C "${STAGE_DIR}" .
echo "DONE: ${OUT_NAME}.tar.gz"
echo "Transfer to the test server and run unpack.sh there."
