#!/usr/bin/env bash
#
# unpack.sh - One-click restore a pack onto a test server.
#
# Expects a tarball produced by pack.sh (dev server). This script:
#   1. Loads N4-monitor self-built images (docker load)
#   2. Places the N4-monitor repo + .env under /opt/N4-monitor
#   3. Places middleware compose definitions under /opt/<name> (definitions only)
#   4. Starts everything with docker compose up -d (middleware pulls official
#      images itself; N4-monitor uses the loaded images)
#
# IMPORTANT: no business data is transferred. MySQL/TDengine/Redis start empty
# (fresh volumes) - the test env initializes cleanly. See deploy/README.md.
#
# Usage:
#   sudo ./unpack.sh n4-monitor-pack-XXXX.tar.gz
#   (run on the TEST server, as root or with docker group)
#
set -euo pipefail

TARBALL="${1:?Usage: $0 <pack.tar.gz>}"
TARGET_ROOT="/opt"

# Resolve the tarball to an absolute path so the script works regardless of the
# current working directory (e.g. run from /opt/N4-monitor/deploy while the pack
# sits in /opt).
case "$TARBALL" in
  /*) ;;
  *)  TARBALL="$(cd "$(dirname "$TARBALL")" && pwd)/$(basename "$TARBALL")" ;;
esac

if [ ! -f "$TARBALL" ]; then
  echo "ERROR: $TARBALL not found" >&2
  exit 1
fi

STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT

echo "==> Extracting $TARBALL ..."
tar -xzf "$TARBALL" -C "$STAGE_DIR"

# ---- 1. Load N4-monitor images ---------------------------------------------
if [ -d "${STAGE_DIR}/images" ]; then
  echo "==> Loading N4-monitor images ..."
  for f in "${STAGE_DIR}"/images/*.tar; do
    [ -e "$f" ] || continue
    echo "    - $(basename "$f")"
    docker load -i "$f"
  done
else
  echo "WARN: no images found in pack"
fi

# ---- 2. N4-monitor repo ----------------------------------------------------
if [ -d "${STAGE_DIR}/N4-monitor" ]; then
  echo "==> Installing N4-monitor to ${TARGET_ROOT}/N4-monitor ..."
  mkdir -p "${TARGET_ROOT}"
  # Fresh checkout each time (repo content is fully present in the pack).
  rm -rf "${TARGET_ROOT}/N4-monitor"
  cp -r "${STAGE_DIR}/N4-monitor" "${TARGET_ROOT}/N4-monitor"
else
  echo "ERROR: N4-monitor not present in pack" >&2
  exit 1
fi

# ---- 3. Middleware compose definitions + mount dirs ------------------------
if [ -d "${STAGE_DIR}/middleware" ]; then
  echo "==> Installing middleware compose definitions and mount dirs ..."
  for p in "${STAGE_DIR}/middleware"/*/; do
    [ -d "$p" ] || continue
    name="$(basename "$p")"
    echo "    - ${name}"
    mkdir -p "${TARGET_ROOT}/${name}"
    # Copy the whole staged dir (compose yaml + env + config dirs + empty data
    # dirs). Preserve structure so bind-mount sources exist before startup.
    cp -r "$p". "${TARGET_ROOT}/${name}/"
  done
else
  echo "WARN: no middleware definitions in pack"
fi

# ---- 4. Start middleware (pull official images, empty data volumes) --------
echo "==> Starting middleware containers ..."
for name in emqx fuxa kafka mysql redis tdengine; do
  if [ -f "${TARGET_ROOT}/${name}/docker-compose.yml" ] \
     || [ -f "${TARGET_ROOT}/${name}/docker-compose.yaml" ] \
     || [ -f "${TARGET_ROOT}/${name}/compose.yml" ]; then
    echo "    - up ${name}"
    ( cd "${TARGET_ROOT}/${name}" && docker compose up -d ) || echo "WARN: ${name} failed to start (check config)"
  else
    echo "    - SKIP ${name} (no compose file)"
  fi
done

# ---- 5. Start N4-monitor --------------------------------------------------
# Prefer the images already loaded from the pack (docker load in step 1) so a
# test server never rebuilds (Maven central / npm downloads are slow or blocked
# on restricted networks). Only build when an image is missing - e.g. a pack
# produced without that image, or a local source change the operator wants.
echo "==> Starting N4-monitor ..."
cd "${TARGET_ROOT}/N4-monitor"

NEED_BUILD=0
for img in "n4-monitor-backend:latest" "n4-monitor-frontend:latest"; do
  if ! docker image inspect "$img" >/dev/null 2>&1; then
    echo "    - image $img not present locally, will build"
    NEED_BUILD=1
  fi
done

if [ "$NEED_BUILD" = "1" ]; then
  echo "==> Building missing N4-monitor images ..."
  docker compose build n4-monitor-backend n4-monitor-frontend 2>/dev/null || true
fi

docker compose up -d

echo ""
echo "DONE. Verify:"
echo "  docker compose ps"
echo "  docker compose logs -f n4-monitor-backend"
echo ""
echo "Reminders:"
echo "  - Middleware data volumes are FRESH (empty). Apply any seed/schema if needed."
echo "  - If the test server IP differs, update .env under ${TARGET_ROOT}/N4-monitor"
echo "    (and middleware .env) to the new IPs, then docker compose up -d again."
