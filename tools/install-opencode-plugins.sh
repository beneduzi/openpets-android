#!/usr/bin/env bash
# Install the bundled OpenPets host plugins into your OpenCode plugins directory.
#
#   tools/install-opencode-plugins.sh [--dry-run]
#
# Copies integrations/opencode-plugin/*.ts to
#   ${XDG_CONFIG_HOME:-$HOME/.config}/opencode/plugins/
# (idempotent; safe to re-run).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/../integrations/opencode-plugin"
CONFIG_HOME="${XDG_CONFIG_HOME:-$HOME/.config}"
DEST_DIR="$CONFIG_HOME/opencode/plugins"

dry_run=0
if [ "${1:-}" = "--dry-run" ]; then
  dry_run=1
fi

if [ ! -d "$SRC_DIR" ]; then
  echo "error: source plugins not found at $SRC_DIR" >&2
  exit 1
fi

shopt -s nullglob
plugins=("$SRC_DIR"/*.ts)
if [ ${#plugins[@]} -eq 0 ]; then
  echo "error: no .ts plugins found in $SRC_DIR" >&2
  exit 1
fi

mkdir -p "$DEST_DIR"
echo "OpenPets host plugins -> $DEST_DIR"
for f in "${plugins[@]}"; do
  name="$(basename "$f")"
  if [ "$dry_run" = 1 ]; then
    echo "  would copy $name"
  else
    cp -f "$f" "$DEST_DIR/$name"
    echo "  copied $name"
  fi
done

cat <<'EOF'

Next steps:
  1) Make sure a discovery file exists:
       tools/write-discovery.sh <DEVICE_IP> <IPC_TOKEN>
     (or set OPENPETS_DISCOVERY_FILE to your discovery file)
  2) For the two-way features (ask / confirm / media / approvals / voice),
     enable the app's Settings -> "WebSocket (experimental)".
  3) Reload OpenCode so the plugins load:
       opencode service restart
EOF
