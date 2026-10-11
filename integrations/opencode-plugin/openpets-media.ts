/**
 * openpets-media — host-side tool to display an image on the OpenPets pet.
 *
 * Registers the `media` tool: give it a local file `path` (png/jpg/jpeg/webp/gif)
 * OR an http(s) `url`, plus an optional `caption`/`durationMs`, and it sends
 * `pet.showMedia` over the IPC channel (discovery file). Local files are read and
 * sent inline as base64 (no network on the device).
 *
 * Install: copy to `~/.config/opencode/plugins/openpets-media.ts`, then restart
 * the OpenCode service (or it hot-loads).
 */
import { readFileSync } from "node:fs";
import { connect } from "node:net";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import { homedir } from "node:os";

const MAX_BYTES = Number(process.env.OPENPETS_MEDIA_MAX_BYTES || 10 * 1024 * 1024);

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

function ipc(method: string, params: Record<string, unknown>, timeoutMs = 20000): Promise<{ ok?: boolean; error?: unknown } | undefined> {
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

export default {
  id: "openpets-media",
  setup: (ctx: { tool?: { transform: (cb: (editor: { namespace: (n: { name: string; description: string }) => void; add: (t: unknown) => void }) => void) => Promise<unknown> } }) => {
    if (!readDiscovery()) return;
    void ctx.tool?.transform((editor) => {
      editor.namespace({ name: "openpets", description: "Show images and status on the OpenPets pet" });
      editor.add({
        name: "media",
        description: "Display an image on the OpenPets pet. Provide a local file `path` (png/jpg/jpeg/webp/gif) or an http(s) `url`, with an optional caption.",
        input: {
          type: "object",
          properties: {
            path: { type: "string", description: "Local image file path on this host." },
            url: { type: "string", description: "http(s) image URL." },
            caption: { type: "string", description: "Optional caption (max ~140 chars)." },
            durationMs: { type: "integer", description: "How long to show it (1000-30000)." },
          },
          required: [],
          additionalProperties: false,
        },
        execute: async (input: unknown) => {
          const { path, url, caption, durationMs } = input as { path?: string; url?: string; caption?: string; durationMs?: number };
          if (!path && !url) return { content: "Provide either a local `path` or an http(s) `url`." };
          const params: Record<string, unknown> = {};
          if (caption) params.message = caption.slice(0, 140);
          if (typeof durationMs === "number") params.durationMs = Math.min(Math.max(durationMs, 1000), 30000);
          if (url) {
            params.url = url;
          } else {
            try {
              const bytes = readFileSync(path!);
              if (bytes.length > MAX_BYTES) return { content: `Image too large (${bytes.length} bytes; max ${MAX_BYTES}).` };
              params.data = bytes.toString("base64");
            } catch (e) {
              return { content: `Could not read file: ${String(e)}` };
            }
          }
          const r = await ipc("pet.showMedia", params);
          return { content: r?.ok ? "Shown on the pet." : `Failed: ${JSON.stringify(r?.error ?? r ?? "no response")}` };
        },
      });
    });
  },
};
