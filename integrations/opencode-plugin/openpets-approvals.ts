/**
 * openpets-approvals — EXPERIMENTAL host-side approval flow for the OpenPets
 * Android companion (the host half of "interactive approvals on the pet").
 *
 * Requires the app's EXPERIMENTAL WebSocket endpoint to be enabled
 * (Settings -> "WebSocket (experimental)"), default `ws://<device>:18423`.
 *
 * Flow:
 *   1. When a permission (approval) request is raised, this plugin shows a
 *      pinned bubble on the pet with Approve / Always / Deny buttons.
 *   2. It keeps a WebSocket connection to the app and receives your tap as
 *      `{ "type":"event", "event":"action", "bubbleId":..., "actionId":... }`.
 *   3. On a matching tap it calls `ctx.permission.reply(...)` to resolve it.
 *
 * EXPERIMENTAL / caveats:
 *   - The OpenCode permission *event* payload is not in the public schema; the
 *     request id/session id are extracted defensively from several shapes. If
 *     your build differs, adjust `extractRequest(...)` / `REQUEST_FIELDS`.
 *   - Question-tool answers cannot be injected back into the tool by a plugin,
 *     so this covers permission approvals only. Questions are still notified
 *     by the main `openpets.ts` plugin.
 *   - If the WebSocket endpoint is disabled or unreachable, this plugin is a
 *     no-op and approvals are answered normally in the TUI.
 *
 * Install: copy to `~/.config/opencode/plugins/openpets-approvals.ts` (or
 * `.opencode/plugins/`), then restart the OpenCode service.
 */
import { readFileSync } from "node:fs";
import { connect, type Socket } from "node:net";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import { homedir } from "node:os";

type Reply = "once" | "always" | "reject";

function discoveryPath(): string {
  if (process.env.OPENPETS_DISCOVERY_FILE) return process.env.OPENPETS_DISCOVERY_FILE;
  const base = process.env.XDG_CONFIG_HOME || join(homedir(), ".config");
  return join(base, "opencode", "openpets-discovery.json");
}

function readDiscovery(): { host: string; port: number; token: string } | undefined {
  try {
    const d = JSON.parse(readFileSync(discoveryPath(), "utf8")) as { endpoint?: string; token?: string };
    const m = /^tcp:\/\/([^:/]+):(\d+)$/.exec(d.endpoint ?? "");
    if (!m || typeof d.token !== "string") return undefined;
    return { host: m[1], port: Number(m[2]), token: d.token };
  } catch {
    return undefined;
  }
}

/** Send an IPC request straight to the device (pet.say with buttons). */
function sendIpc(discovery: { host: string; port: number; token: string }, method: string, params: Record<string, unknown>): void {
  try {
    const socket: Socket = connect({ host: discovery.host, port: discovery.port });
    socket.setTimeout(800);
    socket.on("connect", () => {
      socket.write(JSON.stringify({ id: randomUUID(), version: 1, token: discovery.token, method, params }) + "\n");
    });
    socket.on("data", () => socket.end());
    socket.on("error", () => {});
    socket.on("timeout", () => socket.destroy());
  } catch {
    /* never break the host */
  }
}

const WS_PORT = Number(process.env.OPENPETS_WS_PORT || 18423);

// bubbleId -> pending approval
const pending = new Map<string, { sessionID: string; requestID: string }>();
let ws: WebSocket | undefined;

function ensureWs(discovery: { host: string; token: string }): void {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;
  const GlobalWebSocket = (globalThis as { WebSocket?: typeof WebSocket }).WebSocket;
  if (!GlobalWebSocket) return; // older Node: no built-in client
  try {
    ws = new GlobalWebSocket(`ws://${discovery.host}:${WS_PORT}/?token=${encodeURIComponent(discovery.token)}`);
    ws.addEventListener("message", (event: MessageEvent) => {
      try {
        const msg = JSON.parse(typeof event.data === "string" ? event.data : "");
        if (msg?.type !== "event" || msg?.event !== "action") return;
        const actionId = String(msg.actionId ?? "");
        const bubbleId = typeof msg.bubbleId === "string" ? msg.bubbleId : "";
        const request = pending.get(bubbleId);
        if (!request) return;
        const reply: Reply | undefined =
          actionId === "approve" ? "once" : actionId === "always" ? "always" : actionId === "deny" ? "reject" : undefined;
        if (!reply) return;
        pending.delete(bubbleId);
        void onReply(request.sessionID, request.requestID, reply, reply === "reject" ? "error" : "success");
      } catch {
        /* ignore malformed frames */
      }
    });
    ws.addEventListener("close", () => { ws = undefined; });
    ws.addEventListener("error", () => {});
  } catch {
    /* ignore */
  }
}

async function onReply(sessionID: string, requestID: string, reply: Reply, reaction: string): Promise<void> {
  // Best-effort: resolve the permission request through the plugin context.
  try {
    await (globalThis as { __openpetsCtx?: { permission?: { reply?: (input: unknown) => Promise<void> } } })
      .__openpetsCtx?.permission?.reply?.({ sessionID, requestID, reply });
  } catch {
    /* fall through to IPC feedback only */
  }
  sendIpc(readDiscovery() ?? { host: "", port: 0, token: "" }, "pet.react", { reaction });
}

/** Defensively pull {sessionID, requestID} out of an unknown event payload. */
function extractRequest(raw: unknown): { sessionID: string; requestID: string } | undefined {
  const e = raw as {
    type?: string;
    properties?: Record<string, unknown>;
    payload?: { properties?: Record<string, unknown>; request?: Record<string, unknown> };
    request?: Record<string, unknown>;
  };
  const p: Record<string, unknown> = e?.request ?? e?.properties ?? e?.payload?.request ?? e?.payload?.properties ?? {};
  const sessionID = p["sessionID"] ?? p["sessionId"] ?? p["session"];
  const requestID = p["id"] ?? p["requestID"] ?? p["requestId"];
  if (typeof sessionID === "string" && typeof requestID === "string") return { sessionID, requestID };
  return undefined;
}

export default {
  id: "openpets-approvals",
  setup: (ctx: {
    permission?: { hook: (name: "evaluate", cb: (event: unknown) => unknown) => unknown; reply?: (input: unknown) => Promise<void> };
    event?: { subscribe: () => AsyncIterable<unknown> };
  }) => {
    // Expose the plugin context so async WS callbacks can resolve approvals.
    (globalThis as { __openpetsCtx?: unknown }).__openpetsCtx = ctx;

    const discovery = readDiscovery();
    if (!discovery) return; // no device configured

    // Publish an approval bubble on the pet for a request.
    const showBubble = (sessionID: string, requestID: string, action: string, resources: string[]) => {
      const bubbleId = `perm:${requestID}`;
      pending.set(bubbleId, { sessionID, requestID });
      ensureWs(discovery);
      sendIpc(discovery, "pet.say", {
        message: `Approve: ${action}`.slice(0, 140),
        reaction: "waiting",
        id: bubbleId,
        pinned: true,
        actions: [
          { id: "approve", label: "Approve", style: "primary" },
          { id: "always", label: "Always", style: "default" },
          { id: "deny", label: "Deny", style: "danger" },
        ],
      });
      void resources;
    };

    try {
      ctx?.permission?.hook?.("evaluate", (event) => {
        const e = event as { effect?: string; sessionID?: string; action?: string; resources?: string[] };
        if (e?.effect === "ask" && typeof e.sessionID === "string" && typeof e.action === "string") {
          // No request id here; publish a bubble once the request is known via the event stream.
          void showBubble(e.sessionID, `pending:${Date.now()}`, e.action, e.resources ?? []);
        }
      });
    } catch {
      /* ignore */
    }

    try {
      void (async () => {
        try {
          for await (const raw of ctx.event!.subscribe()) {
            const type = (raw as { type?: string })?.type ?? "";
            if (type.includes("permission") && type.includes("ask")) {
              const req = extractRequest(raw);
              if (req) showBubble(req.sessionID, req.requestID, "a tool action", []);
            }
          }
        } catch {
          /* stream ended */
        }
      })();
    } catch {
      /* ignore */
    }
  },
};
