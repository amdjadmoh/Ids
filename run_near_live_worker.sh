#!/bin/sh
set -eu

if [ $# -lt 1 ]; then
  echo "Usage: sudo sh run_near_live_worker.sh <interface> [chunk-seconds]" >&2
  exit 1
fi

INTERFACE="$1"
CHUNK_SECONDS="${2:-20}"

cd "$(dirname "$0")"
exec python3 tools/near_live_worker.py --interface "$INTERFACE" --chunk-seconds "$CHUNK_SECONDS"
