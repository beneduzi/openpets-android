/**
 * openpets-move — host-side tools to puppeteer the OpenPets pet.
 *
 * Registers two tools:
 *   - `move`  { x, y?, durationMs? }  → walks the pet to a position (walks with
 *              the directional sprites).
 *   - `react` { reaction }            → plays a reaction animation.
 *
 * So a session can do: move right → left → right → idle → celebrate.
 *
 * Install: copy to `~/.config/opencode/plugins/openpets-move.ts` (or run
 * tools/install-opencode-plugins.sh).
 */
import { readFileSync } from "node:fs";
import { connect } from "node:net";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import { homedir } from "node:os";

const REACTIONS = ["idle", "thinking", "working", "editing", "running", "testing", "waiting", "waving", "success", "error", "celebrating"];

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

function ipc(method: string, params: Record<string, unknown>, timeoutMs = 4000): Promise<{ ok?: boolean; error?: unknown } | undefined> {
  return new Promise((resolve) => {
    const d = readDiscovery();
    if (!d) return resolve(undefined);
    const socket = connect({ host: d.host, port: d.port });
    socket.setTimeout(timeoutMs);
    let buffer = "";
    let done = false;
    const finish = (v: { ok?: boolean; error?: unknown } | undefined) => {
      if (done) return;
      done = true;
      try {
        socket.destroy();
      } catch {
        /* ignore */
      }
      resolve(v);
    };
    socket.on("connect", () => socket.write(JSON.stringify({ id: randomUUID(), version: 1, token: d.token, method, params }) + "\n"));
    socket.on("data", (chunk) => {
      buffer += chunk.toString("utf8");
      const nl = buffer.indexOf("\n");
      if (nl >= 0) {
        try {
          finish(JSON.parse(buffer.slice(0, nl)));
        } catch {
          finish(undefined);
        }
      }
    });
    socket.on("error", () => finish(undefined));
    socket.on("timeout", () => finish(undefined));
  });
}

const ok = (r: { ok?: boolean; error?: unknown } | undefined, label: string) =>
  ({ content: r?.ok ? label : `Failed: ${JSON.stringify(r?.error ?? r ?? "no response")}` });

export default {
  id: "openpets-move",
  setup: (ctx: { tool?: { transform: (cb: (editor: { namespace: (n: { name: string; description: string }) => void; add: (t: unknown) => void }) => void) => Promise<unknown> } }) => {
    if (!readDiscovery()) return;
    void ctx.tool?.transform((editor) => {
      editor.namespace({ name: "openpets", description: "Move and animate the OpenPets pet" });
      editor.add({
        name: "move",
        description: "Walk the OpenPets pet to a position. x and y are fractions 0..1 of the screen (left..right / top..bottom); durationMs sets the walking speed.",
        input: {
          type: "object",
          properties: {
            x: { type: "number", description: "Horizontal fraction 0 (left) .. 1 (right)." },
            y: { type: "number", description: "Optional vertical fraction 0 (top) .. 1 (bottom)." },
            durationMs: { type: "integer", description: "Walk duration (0-10000 ms; default 300)." },
          },
          required: ["x"],
          additionalProperties: false,
        },
        execute: async (input: unknown) => {
          const { x, y, durationMs } = input as { x: number; y?: number; durationMs?: number };
          const params: Record<string, unknown> = { x: Math.min(Math.max(x, 0), 1) };
          if (typeof y === "number") params.y = Math.min(Math.max(y, 0), 1);
          if (typeof durationMs === "number") params.durationMs = Math.min(Math.max(durationMs, 0), 10000);
          const r = await ipc("pet.move", params);
          // Wait for the walk to finish so sequential move/react calls play in order.
          const wait = Math.min((typeof durationMs === "number" ? durationMs : 300) + 60, 10050);
          await new Promise((res) => setTimeout(res, wait));
          return ok(r, "Pet moved.");
        },
      });
      editor.add({
        name: "react",
        description: "Play a reaction animation on the OpenPets pet (idle/thinking/working/editing/running/testing/waiting/waving/success/error/celebrating).",
        input: {
          type: "object",
          properties: { reaction: { type: "string", enum: REACTIONS } },
          required: ["reaction"],
          additionalProperties: false,
        },
        execute: async (input: unknown) => {
          const { reaction } = input as { reaction: string };
          return ok(await ipc("pet.react", { reaction }), `Reaction: ${reaction}.`);
        },
      });
    });
  },
};
