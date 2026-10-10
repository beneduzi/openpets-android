/**
 * Template host plugin for the OpenPets Android companion.
 *
 * Demonstrates the full loop:
 *   1. tell the pet to show a pinned bubble with Approve / Deny buttons,
 *   2. receive the user's tap over the experimental WebSocket,
 *   3. react to it.
 *
 * Run:  node plugin.mjs
 * Needs: a discovery file, and the app's "WebSocket (experimental)" enabled
 *        (for the inbound tap). Outbound messages work over IPC even without WS.
 *
 * Copy this folder, rename it, and replace the demo logic with your own.
 */
import { say, react, connectWs } from "../openpets-client.mjs";

const BUBBLE_ID = "host-plugin-demo";

// Outbound: show an actionable bubble on the pet.
async function askToDeploy() {
  await react("thinking");
  await say("Deploy to production?", {
    bubbleId: BUBBLE_ID,
    pinned: true,
    source: "host-plugin", // gives the bubble its own per-project accent
    actions: [
      { id: "approve", label: "Approve", style: "primary" },
      { id: "deny", label: "Deny", style: "danger" },
    ],
  });
}

// Inbound: the user tapped a button on the pet.
connectWs({
  onEvent: (event) => {
    if (event?.type !== "event" || event?.event !== "action") return;
    if (event.bubbleId !== BUBBLE_ID) return;
    if (event.actionId === "approve") {
      void say("Approved — rolling out", { reaction: "success" });
      // …your real action here…
    } else if (event.actionId === "deny") {
      void say("Cancelled", { reaction: "error" });
    }
  },
  onOpen: () => console.error("host-plugin: connected to the pet"),
  onError: () => console.error("host-plugin: WS error (is the app's WebSocket enabled?)"),
});

// Kick off the demo shortly after startup.
setTimeout(askToDeploy, 1500);
