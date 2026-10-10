/**
 * openpets-voice — EXPERIMENTAL host-side voice-input consumer.
 *
 * Connects to the OpenPets Android app's experimental WebSocket endpoint and
 * listens for `input.text` events (the transcript of your dictation on the
 * phone). When one arrives, it injects the text as a user message into
 * OpenCode so the coding agent receives it as input.
 *
 * Requires:
 *   - The app's experimental WebSocket endpoint enabled (Settings).
 *   - The app's Voice input used to dictate (microphone is opt-in on the phone).
 *
 * Target session:
 *   - `OPENPETS_VOICE_SESSION` (env) to pin a session id, else the most
 *     recently listed session is used (best effort).
 *
 * Install: copy to `~/.config/opencode/plugins/openpets-voice.ts` (or
 * `.opencode/plugins/`), then restart the OpenCode service.
 *
 * EXPERIMENTAL: the app frame schema may change; the session targeting is
 * best-effort. The plugin never throws into the host.
 */
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

const WS_PORT = Number(process.env.OPENPETS_WS_PORT || 18423);
const MAX_TEXT = Number(process.env.OPENPETS_VOICE_MAX_CHARS || 2000);

function discovery(): { host: string; token: string } | undefined {
  const path = process.env.OPENPETS_DISCOVERY_FILE ||
    join(process.env.XDG_CONFIG_HOME || join(homedir(), ".config"), "opencode", "openpets-discovery.json");
  try {
    const d = JSON.parse(readFileSync(path, "utf8")) as { endpoint?: string; token?: string };
    const m = /^tcp:\/\/([^:/]+):(\d+)$/.exec(d.endpoint ?? "");
    if (!m || typeof d.token !== "string") return undefined;
    return { host: m[1], token: d.token };
  } catch {
    return undefined;
  }
}

export default {
  id: "openpets-voice",
  setup: (ctx: {
    session?: {
      list?: (input?: unknown) => Promise<readonly { id: string }[]>;
      prompt?: (input: { sessionID: string; text: string }) => Promise<unknown>;
    };
  }) => {
    const disc = discovery();
    if (!disc) return;
    const GlobalWebSocket = (globalThis as { WebSocket?: typeof WebSocket }).WebSocket;
    if (!GlobalWebSocket) return;

    const last = { text: "", at: 0 };

    async function inject(text: string): Promise<void> {
      const clean = text.trim().slice(0, MAX_TEXT);
      if (!clean) return;
      const now = Date.now();
      if (clean === last.text && now - last.at < 1500) return; // ignore rapid duplicates
      last.text = clean;
      last.at = now;
      try {
        let sessionID = process.env.OPENPETS_VOICE_SESSION;
        if (!sessionID) {
          const sessions = (await ctx.session?.list?.({})) ?? [];
          sessionID = sessions[0]?.id;
        }
        if (!sessionID) return;
        await ctx.session?.prompt?.({ sessionID, text: clean });
      } catch {
        /* ignore */
      }
    }

    const url = `ws://${disc.host}:${WS_PORT}/?token=${encodeURIComponent(disc.token)}`;
    const connect = () => {
      try {
        const ws = new GlobalWebSocket(url);
        ws.addEventListener("message", (event: MessageEvent) => {
          try {
            const msg = JSON.parse(typeof event.data === "string" ? event.data : "");
            if (msg?.type === "event" && msg?.event === "input.text" && typeof msg.text === "string") {
              void inject(msg.text);
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
  },
};
