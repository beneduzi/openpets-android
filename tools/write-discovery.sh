#!/usr/bin/env bash
# Writes an OpenPets discovery JSON pointing agents at the Android companion.
#
# usage: write-discovery.sh <DEVICE_IP> <IPC_TOKEN> [IPC_PORT] [OUT_PATH]
set -euo pipefail

HOST=${1:?usage: write-discovery.sh <DEVICE_IP> <IPC_TOKEN> [IPC_PORT] [OUT_PATH]}
TOKEN=${2:?missing IPC token}
PORT=${3:-18420}
OUT=${4:-$HOME/.config/opencode/openpets-discovery.json}

case "$(uname -s)" in
  Darwin) PLATFORM=darwin ;;
  MINGW*|MSYS*|CYGWIN*) PLATFORM=win32 ;;
  *) PLATFORM=linux ;;
esac

mkdir -p "$(dirname "$OUT")"
cat > "$OUT" <<JSON
{
  "protocol": "openpets-ipc",
  "protocolVersion": 1,
  "endpoint": "tcp://${HOST}:${PORT}",
  "token": "${TOKEN}",
  "appVersion": "0.1.0",
  "pid": $$,
  "platform": "${PLATFORM}"
}
JSON

echo "Wrote ${OUT}"
cat "${OUT}"
