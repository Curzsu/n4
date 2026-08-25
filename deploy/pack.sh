#!/usr/bin/env bash
#
# pack.sh - One-click pack the dev environment for migration to a test server.
#
# What is packed:
#   1. N4-monitor code repo + root .env (compose reads it for interpolation)
#   2. N4-monitor self-built images (backend/frontend/flink) via docker save
#   3. Custom / locally-built middleware images that the test server cannot pull
#      from a registry (e.g. FUXA) via docker save
#   4. Middleware compose definitions AND their bind-mount source directories:
#      - config dirs (emqx etc, nginx conf, ...) are packed fully (config lives)
#      - pure data dirs (mysql data, tdengine data, redis/kafka data) are packed
#        as EMPTY structure only (business data is NOT migrated; the test env
#        starts fresh but the mount points exist so containers can start)
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

# N4-monitor self-built images to export (docker save). Keep in sync with
# docker-compose.yml. These are NOT pullable on the test server.
N4_IMAGES=("n4-monitor-backend:latest" "n4-monitor-frontend:latest" "n4-monitor-flink-job:latest")

# Additional images that exist ONLY on the dev server (custom/self-uploaded or
# pulled once and never re-pullable). Add images the test server cannot fetch.
# FUXA is the known one here (self-uploaded, not on a public registry).
EXTRA_IMAGES=("frangoteam/fuxa:1.3.3")

# Subdirectory names that are PURE business data or runtime-only output - their
# content is NOT packed (structure only). Adjust per actual middleware compose:
#   data/db        -> database files, DAQ history, RDB/AOF (regenerate on test)
#   log/logs       -> runtime logs
# NOTE: config dirs that hold real config (emqx etc, mysql conf, redis conf) are
# NOT in this list and are copied in full, as is fuxa's appdata (its FUXA
# project / HMI screens are a hand-built config that would have to be redone if
# lost).
DATA_DIRS=("data" "mysql-data" "mysqldata" "tdengine-data" "kafka-data" "redis-data" "db" "databases" "log" "logs")

OUT_NAME="${1:-n4-monitor-pack-$(date +%Y%m%d-%H%M%S)}"
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT

echo "==> Packing to: ${OUT_NAME}.tar.gz"

# ---- Helper: classify a bind-mount source path as data or config -------------
is_data_dir() {
  local p="$1"
  local name
  name="$(basename "$p")"
  for d in "${DATA_DIRS[@]}"; do
    [ "$name" = "$d" ] && return 0
  done
  return 1
}

# ---- 1. N4-monitor repo (code + .env) --------------------------------------
if [ ! -d "${DEV_ROOT}/N4-monitor" ]; then
  echo "ERROR: ${DEV_ROOT}/N4-monitor not found" >&2
  exit 1
fi

echo "==> Rebuilding N4-monitor images (ensures the pack carries the latest build)..."
(
  cd "${DEV_ROOT}/N4-monitor"
  docker compose build n4-monitor-backend n4-monitor-frontend 2>/dev/null \
    || docker compose build 2>/dev/null \
    || echo "WARN: compose build failed; exporting whatever images exist"
)

echo "==> Copying N4-monitor repo (git-tracked files + .env)..."
mkdir -p "${STAGE_DIR}/N4-monitor"
cd "${DEV_ROOT}/N4-monitor"
git ls-files -z | xargs -0 -I{} cp --parents "{}" "${STAGE_DIR}/N4-monitor/" 2>/dev/null || true
[ -f .env ] && cp .env "${STAGE_DIR}/N4-monitor/.env" || echo "WARN: no .env in N4-monitor"
[ -f .env.example ] && cp .env.example "${STAGE_DIR}/N4-monitor/.env.example" || true

# ---- 2. Images -------------------------------------------------------------
echo "==> Exporting images via docker save..."
mkdir -p "${STAGE_DIR}/images"
ALL_IMAGES=("${N4_IMAGES[@]}" "${EXTRA_IMAGES[@]}")
for img in "${ALL_IMAGES[@]}"; do
  if docker image inspect "$img" >/dev/null 2>&1; then
    echo "    - ${img}"
    docker save "$img" -o "${STAGE_DIR}/images/$(echo "$img" | tr '/:' '__').tar"
  else
    echo "    - SKIP ${img} (not present)"
  fi
done

# ---- 3. Middleware compose definitions + bind-mount sources ------------------
echo "==> Copying middleware compose definitions and mount dirs..."
for p in "${PROJECTS[@]}"; do
  [ "$p" = "N4-monitor" ] && continue
  if [ ! -d "${DEV_ROOT}/${p}" ]; then
    echo "    - SKIP ${p} (not found)"
    continue
  fi

  mkdir -p "${STAGE_DIR}/middleware/${p}"

  # 3a. Compose definition files + env files (flat copy).
  find "${DEV_ROOT}/${p}" -maxdepth 2 \
    \( -name 'docker-compose.yml' -o -name 'docker-compose.yaml' -o -name 'compose.yml' \
       -o -name 'compose.yaml' -o -name '.env' \) \
    -type f -exec cp {} "${STAGE_DIR}/middleware/${p}/" \; 2>/dev/null || true

  # 3b. Bind-mount source directories referenced by the compose file.
  #     Parse volumes lines of the form  ./src:/dest  or  src:/dest  (relative).
  COMPOSE_FILE="${DEV_ROOT}/${p}/docker-compose.yml"
  [ -f "$COMPOSE_FILE" ] || COMPOSE_FILE="${DEV_ROOT}/${p}/docker-compose.yaml"
  [ -f "$COMPOSE_FILE" ] || COMPOSE_FILE="${DEV_ROOT}/${p}/compose.yml"
  [ -f "$COMPOSE_FILE" ] || COMPOSE_FILE="${DEV_ROOT}/${p}/compose.yaml"

  if [ -f "$COMPOSE_FILE" ]; then
    # Collect bind-mount sources from the compose file. Three kinds:
    #   ./relative   -> relative to the project dir  (/opt/<name>/<src>)
    #   /absolute    -> absolute path on the host
    #   plain name   -> Docker named volume (SKIP: lives in /var/lib/docker)
    # Host system paths (e.g. /etc/localtime) are skipped - they resolve to the
    # host filesystem and already exist on the test server.
    local_mounts=$(grep -E '^\s*-?\s*(\./|/)' "$COMPOSE_FILE" \
      | sed -E 's/^\s*-?\s*//; s/:.*//' \
      | sort -u || true)

    for src in $local_mounts; do
      [ -z "$src" ] && continue

      # Skip host system paths (localtime, timezone, etc).
      case "$src" in
        /etc/*) echo "    - ${p}:${src} (host system path - skip)"; continue;;
      esac

      # Resolve the source path on THIS host (relative vs absolute).
      case "$src" in
        /*) src_path="$src" ;;
        *)  src_path="${DEV_ROOT}/${p}/${src}" ;;
      esac

      # Staged path relative to middleware/<name> so unpack can restore it.
      # /opt/<name>/x -> x ; other absolute -> basename ; relative -> as-is.
      case "$src" in
        "$DEV_ROOT/${p}"/*) rel="${src#"$DEV_ROOT/${p}"/}" ;;
        *)                  rel="$(basename "$src")" ;;
      esac

      if [ -d "$src_path" ]; then
        if is_data_dir "$src_path"; then
          echo "    - ${p}:${src} (DATA dir - empty structure only)"
          mkdir -p "${STAGE_DIR}/middleware/${p}/${rel}"
        else
          echo "    - ${p}:${src} (config dir - full copy)"
          mkdir -p "${STAGE_DIR}/middleware/${p}/$(dirname "$rel")"
          cp -r "$src_path" "${STAGE_DIR}/middleware/${p}/${rel}"
        fi
      elif [ -f "$src_path" ]; then
        echo "    - ${p}:${src} (config file)"
        mkdir -p "${STAGE_DIR}/middleware/${p}/$(dirname "$rel")"
        cp "$src_path" "${STAGE_DIR}/middleware/${p}/${rel}"
      fi
    done
  fi

  echo "    [done] ${p}"
done

# ---- 4. Assemble tarball ---------------------------------------------------
echo "==> Creating ${OUT_NAME}.tar.gz ..."
tar -czf "${OUT_NAME}.tar.gz" -C "${STAGE_DIR}" .
echo "DONE: ${OUT_NAME}.tar.gz"
echo "Transfer to the test server and run unpack.sh there."
