/**
 * openpets — OpenCode v2 plugin for the OpenPets Android companion.
 *
 * This is the host-side half of the companion. It classifies OpenCode activity
 * and sends IPC requests straight to the pet (via OPENPETS_DISCOVERY_FILE),
 * tagging each event with `source` (the project folder) so the companion can
 * color/label it per project.
 *
 * It does not need the OpenPets MCP to be running: it speaks the IPC protocol
 * directly to the device, so notifications keep working whether or not an MCP
 * client is present.
 *
 * Key behavior: whenever the assistant is blocked waiting for the user — the
 * `question` tool (interactive input prompt) or a `permission.asked` event —
 * the pet is told to show "waiting" and speak an "input needed" bubble. This is
 * the companion's main job. Permission requests are only *announced*: they are
 * answered in the OpenCode terminal (the pet cannot resolve them reliably).
 *
 * Install: copy this file to `~/.config/opencode/plugins/openpets.ts`
 * (global) or `.opencode/plugins/openpets.ts` (project), then restart the
 * OpenCode service so plugins reload. See this folder's README.md.
 */
import { readFileSync } from "node:fs";
import { connect } from "node:net";
import { randomUUID } from "node:crypto";
import { basename, join } from "node:path";
import { homedir, hostname } from "node:os";

const SOURCE = (basename(process.cwd()) || hostname()).slice(0, 64);

function discoveryPath(): string {
  if (process.env.OPENPETS_DISCOVERY_FILE) return process.env.OPENPETS_DISCOVERY_FILE;
  const base = process.env.XDG_CONFIG_HOME || join(homedir(), ".config");
  return join(base, "opencode", "openpets-discovery.json");
}

function send(method: string, params: Record<string, unknown>): void {
  let discovery: { endpoint?: string; token?: string };
  try {
    discovery = JSON.parse(readFileSync(discoveryPath(), "utf8"));
  } catch {
    return;
  }
  const { endpoint, token } = discovery ?? {};
  if (typeof endpoint !== "string" || typeof token !== "string") return;
  const match = /^tcp:\/\/([^:/]+):(\d+)$/.exec(endpoint);
  if (!match) return;

  try {
    const socket = connect({ host: match[1], port: Number(match[2]) });
    socket.setTimeout(600);
    socket.on("connect", () => {
      const request = {
        id: randomUUID(),
        version: 1,
        token,
        method,
        params: { ...params, source: SOURCE },
      };
      socket.write(JSON.stringify(request) + "\n");
    });
    socket.on("data", () => socket.end());
    socket.on("error", () => {});
    socket.on("timeout", () => socket.destroy());
  } catch {
    /* never break the host */
  }
}

const REACTION_COOLDOWN_MS: Record<string, number> = {
  thinking: 10000,
  editing: 10000,
  testing: 10000,
  running: 10000,
  success: 10000,
  error: 3000,
  waiting: 3000,
};
const SPEECH_COOLDOWN_MS = 20000;
const last: Record<string, number> = {};

function throttled(key: string, ms: number): boolean {
  const now = Date.now();
  if ((last[key] ?? 0) + ms > now) return false;
  last[key] = now;
  return true;
}

function react(reaction: string): void {
  if (!throttled(`reaction:${reaction}`, REACTION_COOLDOWN_MS[reaction] ?? 10000)) return;
  send("pet.react", { reaction });
}

function say(category: string, message: string, reaction: string): void {
  if (!throttled(`speech:${category}`, SPEECH_COOLDOWN_MS)) return;
  send("pet.say", { message, reaction });
}

/** The pet's main job: tell the user the assistant is waiting on them. */
function notifyInputNeeded(reason: string, message = "Your input is needed"): void {
  react("waiting");
  say(reason, message, "waiting");
}

function classifyTool(tool: string, args: unknown): string | undefined {
  const normalized = (tool || "").toLowerCase();
  if (/edit|write|patch|apply_patch/.test(normalized)) return "editing";
  if (/bash|shell|terminal/.test(normalized)) {
    const command = args && typeof (args as { command?: unknown }).command === "string"
      ? String((args as { command?: string }).command).slice(0, 300)
      : "";
    return /\b(test|vitest|jest|pytest|npm\s+test|pnpm\s+test|yarn\s+test|cargo\s+test|go\s+test)\b/i.test(command)
      ? "testing"
      : undefined;
  }
  return undefined;
}

function shouldIgnoreTool(tool: string): boolean {
  const t = (tool || "").toLowerCase();
  return /openpets_(status|say|react)$/.test(t) || /openpets_openpets_/.test(t);
}

export default {
  id: "openpets-android-companion",
  setup: (ctx: {
    session?: { hook: (name: string, cb: () => void) => unknown };
    tool?: { hook: (name: string, cb: (event: { tool?: string; input?: unknown }) => void) => unknown };
    event?: { subscribe: () => AsyncIterable<unknown> };
  }) => {
    try {
      ctx?.session?.hook?.("prompt", () => react("thinking"));
    } catch {
      /* ignore */
    }

    try {
      ctx?.tool?.hook?.("execute.before", (event) => {
        const tool = event?.tool ?? "";
        if (shouldIgnoreTool(tool)) return;
        if (/question|ask[_-]?user/i.test(tool)) {
          // The assistant is blocked waiting for the user — the pet's main job is to say so.
          notifyInputNeeded("question");
          return;
        }
        const reaction = classifyTool(tool, event?.input);
        if (reaction) react(reaction);
      });
    } catch {
      /* ignore */
    }

    try {
      void (async () => {
        try {
          for await (const raw of ctx.event!.subscribe()) {
            const event = raw as {
              type?: string;
              payload?: { type?: string; properties?: { status?: { type?: string } } };
              properties?: { status?: { type?: string } };
            };
            const type = event?.type ?? event?.payload?.type;
            if (type === "session.error") {
              react("error");
              say("error", "Something failed", "error");
            } else if (type === "session.status") {
              const status = event?.properties?.status ?? event?.payload?.properties?.status;
              if (status?.type === "idle") react("success");
            } else if (type === "permission.asked") {
              // Approval-only notice: the request is answered in the terminal.
              notifyInputNeeded("permission", "Approval needed - answer in OpenCode");
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
