/**
 * openpets-interact — host-side "answer on the pet" tools.
 *
 * Registers two OpenCode tools, `openpets_ask` and `openpets_confirm`, that
 * show a multiple-choice / yes-no prompt as a pinned pet bubble with buttons and
 * RETURN the tap as the tool result — a real feedback path from the phone to the
 * agent (the built-in `question` tool can't be answered from the pet; a
 * plugin-registered tool can).
 *
 * Requirements:
 *   - The app's experimental WebSocket enabled in Settings (to receive taps).
 *   - A discovery file (OPENPETS_DISCOVERY_FILE or ~/.config/opencode/openpets-discovery.json).
 *
 * Install: copy to `~/.config/opencode/plugins/openpets-interact.ts`, then
 * restart the OpenCode service.
 *
 * EXPERIMENTAL: awaits a WS tap with a bounded timeout; the frame schema may change.
 */
import { readFileSync } from "node:fs";
import { connect } from "node:net";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import { homedir } from "node:os";

const WS_PORT = Number(process.env.OPENPETS_WS_PORT || 18423);
const WAIT_MS = Number(process.env.OPENPETS_ASK_TIMEOUT_MS || 60000);

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

function sendIpc(disc: { host: string; port: number; token: string }, method: string, params: Record<string, unknown>): void {
  try {
    const socket = connect({ host: disc.host, port: disc.port });
    socket.setTimeout(1000);
    socket.on("connect", () => socket.write(JSON.stringify({ id: randomUUID(), version: 1, token: disc.token, method, params }) + "\n"));
    socket.on("data", () => socket.end());
    socket.on("error", () => {});
    socket.on("timeout", () => socket.destroy());
  } catch {
    /* never break the host */
  }
}

// bubbleId -> resolve(actionId)
const pending = new Map<string, (actionId: string | null) => void>();

function ensureWs(disc: { host: string; token: string }): void {
  const GlobalWebSocket = (globalThis as { WebSocket?: typeof WebSocket }).WebSocket;
  if (!GlobalWebSocket) return;
  const connect = () => {
    try {
      const ws = new GlobalWebSocket(`ws://${disc.host}:${WS_PORT}/?token=${encodeURIComponent(disc.token)}`);
      ws.addEventListener("message", (event: MessageEvent) => {
        try {
          const m = JSON.parse(typeof event.data === "string" ? event.data : "");
          if (m?.type === "event" && m?.event === "action" && typeof m.bubbleId === "string") {
            const resolve = pending.get(m.bubbleId);
            if (resolve) {
              pending.delete(m.bubbleId);
              resolve(typeof m.actionId === "string" ? m.actionId : null);
            }
          }
        } catch {
          /* ignore malformed frames */
        }
      });
      ws.addEventListener("error", () => {});
      ws.addEventListener("close", () => setTimeout(connect, 3000));
    } catch {
      /* ignore */
    }
  };
  connect();
}

function showChoices(disc: { host: string; port: number; token: string }, bubbleId: string, message: string, choices: { id: string; label: string }[]): void {
  sendIpc(disc, "pet.say", {
    message: message.slice(0, 140),
    reaction: "waiting",
    id: bubbleId,
    pinned: true,
    actions: choices.slice(0, 4).map((c, i) => ({ id: c.id, label: c.label.slice(0, 40), style: i === 0 ? "primary" : "default" })),
  });
}

function awaitTap(bubbleId: string, send: () => void): Promise<string | null> {
  return new Promise((resolve) => {
    pending.set(bubbleId, resolve);
    send();
    setTimeout(() => {
      if (pending.delete(bubbleId)) resolve(null);
    }, WAIT_MS);
  });
}

export default {
  id: "openpets-interact",
  setup: (ctx: {
    tool?: { transform: (cb: (editor: { namespace: (n: { name: string; description: string }) => void; add: (t: unknown) => void }) => void) => Promise<unknown> };
    session?: { hook: (name: "context", cb: (event: { system: { type: string; text: string }[] }) => void) => unknown };
  }) => {
    const disc = readDiscovery();
    if (!disc) return;
    ensureWs(disc);

    // Automatic routing: steer the agent to use the pet tools for user input, so
    // questions/confirmations land on the pet by default.
    try {
      ctx.session?.hook?.("context", (event) => {
        try {
          event.system.push({
            type: "text",
            text: "When you need input from the user and the OpenPets pet is reachable, prefer the `ask` tool (multiple-choice, answered by tapping the pet) and the `confirm` tool (yes/no approval) over a chat-only prompt, so the user can answer on their device. Fall back to a normal prompt only if those tools are unavailable or time out.",
          });
        } catch {
          /* ignore */
        }
      });
    } catch {
      /* ignore */
    }

    void ctx.tool?.transform((editor) => {
      editor.namespace({ name: "openpets", description: "Ask the user on the OpenPets pet" });
      editor.add({
        name: "ask",
        description: "Ask the user a multiple-choice question; they answer by tapping the OpenPets pet. Returns the selected option.",
        input: {
          type: "object",
          properties: {
            question: { type: "string", description: "The question to show on the pet (max ~140 chars)." },
            options: { type: "array", items: { type: "string" }, minItems: 1, maxItems: 4 },
          },
          required: ["question", "options"],
          additionalProperties: false,
        },
        execute: async (input: unknown) => {
          const { question, options } = input as { question: string; options: string[] };
          const bubbleId = `ask-${randomUUID()}`;
          const actionId = await awaitTap(bubbleId, () => showChoices(disc, bubbleId, question, options.map((o, i) => ({ id: String(i), label: o }))));
          if (actionId === null) return { content: "The user did not answer on the pet (timed out)." };
          const index = Number(actionId);
          const chosen = Number.isInteger(index) && options[index] !== undefined ? options[index] : actionId;
          return { content: `The user selected: ${chosen}` };
        },
      });
      editor.add({
        name: "confirm",
        description: "Ask the user to approve or deny on the OpenPets pet. Returns 'approved' or 'denied'.",
        input: {
          type: "object",
          properties: { message: { type: "string", description: "What to confirm (max ~140 chars)." } },
          required: ["message"],
          additionalProperties: false,
        },
        execute: async (input: unknown) => {
          const { message } = input as { message: string };
          const bubbleId = `confirm-${randomUUID()}`;
          const actionId = await awaitTap(bubbleId, () => showChoices(disc, bubbleId, message, [
            { id: "approve", label: "Approve" },
            { id: "deny", label: "Deny" },
          ]));
          if (actionId === null) return { content: "The user did not respond on the pet (timed out)." };
          return { content: actionId === "approve" ? "The user approved." : "The user denied." };
        },
      });
    });
  },
};
