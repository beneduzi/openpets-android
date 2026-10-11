# OpenCode RPC consolidation (plan — NOT PURSUING)

> **Status: parked / not pursuing (2026-10).** On review this is a large
> host-side (OpenCode plugin) refactor with marginal benefit at the current scale
> of six plugins — it would mainly pay off with many integrations, or if external
> plugins/clients needed one shared contract. **Decision: keep the existing
> standalone plugins**, and instead fix the concrete defects this analysis found
> directly:
>
> - approval wire IDs no longer contain `:` (they were rejected by the app);
> - `openpets-interact` registers its tap resolver *before* sending the bubble
>   (no dropped fast taps);
> - `openpets-client` no longer schedules a reconnect after an intentional close.
>
> Retained for reference in case the integration set grows. Not required for the
> upstream phone-target issue and not a change to the Android wire protocol.
> Derived from the Oracle deep-dive of the OpenCode plugin **RPC** contract
> (`@opencode/plugin/rpc`).

## Recommendation: consolidate behind one RPC broker

Expose **one** `Rpc.define({ id: "openpets", … })` contract and move discovery,
device connections, ownership, arbitration, and pending interactions into a
single lifecycle-managed bridge. Keep activity hooks and model tools as thin
consumers.

RPC should replace the **ad-hoc interface between OpenCode integrations**, not the
Android wire protocol.

```text
OpenCode activity hooks / tools / other plugins / HTTP clients
                         │
                  OpenPets RPC contract
                         │
              one lifecycle-managed bridge
              validation · ownership · queues
                         │
             device IPC + reconnecting WS
                         │
               always-on Android companion
```

Keep the transport implementation OpenCode-independent so standalone Node
integrations and, eventually, the desktop host can reuse it. **Do not introduce a
separate daemon just to consolidate six plugins** — add one only if operation
independent of the OpenCode server is genuinely required.

### Benefits

- One connection manager, discovery validator, reconnect policy, and lease heartbeat.
- Consistent schemas and typed errors instead of inconsistent fire-and-forget sockets.
- Shared arbitration: activity notifications cannot overwrite an unanswered approval.
- One ownership map for bubbles, actions, commands, and explicitly selected voice recipients.
- Cancellation through method `ctx.signal`; bounded waits and deterministic cleanup.
- Other plugins use `ctx.rpc(OpenPetsRpc)`; HTTP clients use `OpenCode.make({ baseUrl }).rpc(OpenPetsRpc)`.
- Device tokens stay in the bridge, not in every consumer.

**RPC registration does not create model tools or grant permissions.** The existing
`ask`, `confirm`, `media`, `move`, `react` tools still need explicit registration
and should proxy only approved RPC methods.

## Defects this analysis found — fixed directly (no RPC needed)

Merely putting the current implementations behind RPC would preserve these:

| Finding | Evidence |
| --- | --- |
| Interaction setup races: the question is sent before its pending resolver is installed, and WS readiness is not awaited. Unknown action IDs can become an arbitrary answer. | `openpets-interact.ts:61–105, 150–179` |
| Approval wire IDs contain `:`, which Android rejects. Pending approvals lack lifecycle cleanup; optional `permission.reply` can do nothing while success feedback is still shown. | `openpets-approvals.ts:105–118, 149–173`; `IpcProtocol.kt:345–353` |
| The shared WS helper returns the original socket, reconnects after intentional closure, and has no stop handle. `sendHostStatus` closes that socket and thereby schedules another connection. | `openpets-client.mjs:131–157, 169–193` |
| IPC response buffers are uncapped and pending calls do not settle explicitly on premature EOF/close. Discovery helpers do not enforce the documented endpoint/token restrictions. | `openpets-client.mjs:39–47, 59–101` |
| Voice falls back to the first listed session rather than an explicitly selected recipient. | `openpets-voice.ts:58–72` |

## Operational rules

1. **Server placement matters.** RPC runs on the OpenCode server, not the TUI
   machine or phone. That server must reach the device. A remotely hosted server
   needs a protected route or local bridge; never solve this by exposing the phone.
2. **One registration per supported scope.** Verify the installed OpenCode
   version's registration/lifecycle scope before assuming a process-wide
   singleton. Workspace instances must not independently register competing owners.
3. **The bridge owns the WS.** Consumers subscribe to RPC events; they do not open
   their own device sockets. Shutdown/reload stops reconnect timers, closes
   sockets, cancels pending interactions, revokes callbacks, and best-effort
   removes bridge-owned UI/commands.
4. **Neither connection is durable.** Reconnection is not replay. Include
   `targetId`, a connection `epoch`, and a bridge sequence number in event data;
   expose a current-state snapshot via `status`; consumers subscribe, snapshot, and
   reconcile by epoch/sequence. Resolve `ask`/`confirm` inside the bridge and return
   the decision as the method result. Never replay approvals, taps, transcripts, or
   notifications.
5. **Ownership is not a payload label.** Bind calls to authenticated caller scope or
   host-issued handles. A supplied `source`, session ID, or event `location` is not
   device ownership.
6. **Fail closed.** Establish WS readiness and install the pending action map before
   displaying a prompt. Accept only mapped, currently allowed IDs; consume one-shot
   interactions before invoking their callback. Timeout/cancellation/replacement
   revokes the map and dismisses the owned bubble.
7. **Define delivery precisely.** Device acks mean accepted, not rendered or
   animation-complete. Do not blindly retry announced effects or approvals.

## What should NOT go through RPC

- Raw `{method, params}` forwarding or arbitrary desktop SDK calls.
- Discovery tokens, socket objects, lease bookkeeping, reconnect controls.
- Android rendering, native reminders/timers, microphone processing, on-device persistence.
- Every animation frame, pointer sample, or telemetry tick — sample/coalesce first.
- Arbitrary server file paths exposed to HTTP clients; media needs authorized
  host-side resolution or staged asset handles.
- An OpenCode permission grant disguised as a generic `confirm`.
- Unowned voice transcripts automatically injected into a session.

## Sketch

> Proposed contract, not shipped. Zod is one supported Standard Schema choice. The
> bridge must additionally apply the phone's content filters, ownership checks,
> authorization, and transport bounds.

```ts
import { Rpc } from "@opencode/plugin/rpc";
import { z } from "zod";

const id = z.string().min(1).max(96);
const target = z.object({ targetId: id.optional() }).strict();
const line = z.string().min(1).max(140).regex(/^[^\r\n]+$/);
const receipt = z.object({ accepted: z.literal(true) }).strict();

const reaction = z.enum([
  "idle", "thinking", "working", "editing", "running", "testing",
  "waiting", "waving", "success", "error", "celebrating",
]);

const errorData = z.object({ targetId: id.optional() }).strict();
const errors = {
  unavailable: errorData, busy: errorData, timeout: errorData,
  unsupported: errorData, not_allowed: errorData, target_failed: errorData,
};

const interaction = {
  requestId: id, // Deduplication/correlation, not caller identity.
  timeoutMs: z.number().int().min(1000).max(120_000).optional(),
};

const origin = z.object({
  targetId: id, epoch: id, seq: z.number().int().nonnegative(),
}).strict();

export const OpenPetsRpc = Rpc.define({
  id: "openpets",
  methods: {
    say: {
      input: target.extend({
        message: line,
        reaction: reaction.optional(),
        pinned: z.boolean().optional(),
        actions: z.array(z.object({
          id: id, // Logical ID; bridge generates phone-compatible wire IDs.
          label: z.string().min(1).max(40),
          style: z.enum(["default", "primary", "danger"]).optional(),
        }).strict()).max(4).optional(),
      }),
      output: receipt.extend({ bubbleId: id }),
      errors,
    },
    dismiss:  { input: target.extend({ bubbleId: id }), output: receipt, errors },
    react:    { input: target.extend({ reaction }), output: receipt, errors },
    move: {
      input: target.extend({
        x: z.number().min(0).max(1),
        y: z.number().min(0).max(1).optional(),
        durationMs: z.number().int().min(0).max(10_000).optional(),
      }),
      output: receipt, errors,
    },
    media: {
      input: target.extend({
        image: z.union([
          z.object({ assetId: id }).strict(),
          z.object({ url: z.string().url().max(2048).regex(/^https?:\/\//) }).strict(),
        ]),
        caption: line.optional(),
        durationMs: z.number().int().min(1000).max(30_000).optional(),
      }),
      output: receipt, errors,
    },
    ask: {
      input: target.extend({
        ...interaction, question: line,
        options: z.array(z.string().min(1).max(40)).min(1).max(4),
      }),
      output: z.object({
        requestId: id, index: z.number().int().min(0).max(3),
        label: z.string().min(1).max(40),
      }).strict(),
      errors,
    },
    confirm: {
      input: target.extend({ ...interaction, message: line }),
      output: z.object({ requestId: id, decision: z.enum(["approved", "denied"]) }).strict(),
      errors,
    },
    status: {
      input: target,
      output: origin.extend({
        connected: z.boolean(),
        capabilities: z.object({
          actions: z.boolean(), commands: z.boolean(), silentUpdates: z.boolean(),
        }).strict(),
        activeBubbleId: id.optional(),
      }),
      errors,
    },
    hostStatus: {
      input: target.extend({
        cpuPct: z.number().min(0).max(100).optional(),
        ramUsedMb: z.number().int().nonnegative().optional(),
        ramTotalMb: z.number().int().positive().optional(),
        diskUsedPct: z.number().min(0).max(100).optional(),
        netRxKbps: z.number().nonnegative().optional(),
        netTxKbps: z.number().nonnegative().optional(),
      }),
      output: receipt, errors,
    },
  },
  events: {
    action:      { schema: origin.extend({ bubbleId: id, actionId: id }) },
    "input.text": { schema: origin.extend({ recipientId: id, text: z.string().min(1).max(2000) }) },
    pointer:     { schema: origin.extend({ kind: z.enum(["tap", "drag"]), x: z.number().min(0).max(1), y: z.number().min(0).max(1) }) },
    power:       { schema: origin.extend({ plugged: z.boolean(), level: z.number().int().min(0).max(100) }) },
    command:     { schema: origin.extend({ registrationId: id, commandId: id }) },
  },
});
```

Registration follows the RPC model:

```ts
const reg = await ctx.rpc.register(OpenPetsRpc, {
  say: (input, call) => bridge.say(input, { signal: call.signal }),
  dismiss: (input, call) => bridge.dismiss(input, { signal: call.signal }),
  react: (input, call) => bridge.react(input, { signal: call.signal }),
  move: (input, call) => bridge.move(input, { signal: call.signal }),
  media: (input, call) => bridge.media(input, { signal: call.signal }),
  ask: async (input, call) => {
    if (!bridge.canInteract(input.targetId)) {
      return call.error("unavailable", "Phone interaction is unavailable", { targetId: input.targetId });
    }
    return bridge.ask(input, { signal: call.signal });
  },
  confirm: (input, call) => bridge.confirm(input, { signal: call.signal }),
  status: (input, call) => bridge.status(input, { signal: call.signal }),
  hostStatus: (input, call) => bridge.hostStatus(input, { signal: call.signal }),
});

bridge.onValidatedAction(data => reg.events.emit("action", data));

await ctx.rpc(OpenPetsRpc).react({ reaction: "working" });
```

Received event types are prefixed (`rpc.openpets.action`); error keys become
`type`; do not define reserved `rpc.*` names.
