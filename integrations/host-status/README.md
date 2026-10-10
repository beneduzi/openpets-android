# Host status sender

Pushes this PC's **CPU / RAM / disk / network** metrics to the OpenPets Android
app's experimental WebSocket endpoint, so the phone can show them beside the
pet — the mirror image of the app's on-device *System Resources*.

## Requirements

- **Node 22+** (uses the built-in global `WebSocket`).
- The app's **WebSocket (experimental)** endpoint enabled in Settings.
- The app's **"Show host status on the pet"** toggle enabled in Settings.
- A discovery file (`OPENPETS_DISCOVERY_FILE`, or
  `~/.config/opencode/openpets-discovery.json`).

## Run

```bash
node send-host-status.mjs
```

It connects to `ws://<device>:18423/?token=<IPC_TOKEN>` (private peers only,
IPC token auth) and sends a `status.host` frame every few seconds:

```json
{
  "type": "status.host",
  "params": {
    "cpuPct": 42, "ramUsedMb": 8192, "ramTotalMb": 16384,
    "diskUsedPct": 61, "netRxKbps": 120, "netTxKbps": 40,
    "loadAvg": 0.8, "ts": 1728576000000
  }
}
```

The app renders it as a pinned HUD (`source: "host"`) and clears it after ~30 s
without updates.

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `OPENPETS_DISCOVERY_FILE` | `~/.config/opencode/openpets-discovery.json` | discovery file path |
| `OPENPETS_WS_PORT` | `18423` | app WebSocket port |
| `OPENPETS_WS_HOST` | host from discovery | override host |
| `OPENPETS_STATUS_INTERVAL_MS` | `5000` | sample interval |

## Platform notes

Linux is fully supported (reads `/proc/stat`, `/proc/meminfo`, `/proc/net/dev`,
`fs.statfs`). On other platforms, metrics that Node cannot read are sent as
`null` and the app shows "unavailable". The first CPU/network sample has no
previous baseline, so it reports `null` until the second tick.
