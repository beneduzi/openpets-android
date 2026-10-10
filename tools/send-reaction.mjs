#!/usr/bin/env node
// Sends an OpenPets IPC (openpets-ipc v1) reaction or speech to the Android companion.
//
// usage:
//   node send-reaction.mjs <host> <port> <ipcToken> react <reaction>
//   node send-reaction.mjs <host> <port> <ipcToken> say "<message>" [reaction]
import net from "node:net";
import { randomUUID } from "node:crypto";

const [host, portArg, token, kind, ...rest] = process.argv.slice(2);
if (!host || !portArg || !token || !kind) {
  console.error(
    'usage: send-reaction.mjs <host> <port> <ipcToken> react <reaction> | say "<message>" [reaction]',
  );
  process.exit(1);
}
const port = Number(portArg);

let method;
let params;
if (kind === "react") {
  method = "pet.react";
  params = { reaction: rest[0] };
} else if (kind === "say") {
  method = "pet.say";
  params = { message: rest[0] };
  if (rest[1]) params.reaction = rest[1];
} else {
  console.error("kind must be 'react' or 'say'");
  process.exit(1);
}

const request = { id: randomUUID(), version: 1, token, method, params };
const socket = net.createConnection({ host, port }, () => {
  socket.write(JSON.stringify(request) + "\n");
});
socket.setEncoding("utf8");
socket.setTimeout(3000, () => {
  console.error("timeout");
  socket.destroy();
  process.exit(3);
});
let buffer = "";
socket.on("data", (chunk) => {
  buffer += chunk;
  const newline = buffer.indexOf("\n");
  if (newline >= 0) {
    console.log(buffer.slice(0, newline));
    socket.end();
  }
});
socket.on("error", (error) => {
  console.error("error:", error.message);
  process.exit(2);
});
