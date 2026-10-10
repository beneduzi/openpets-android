#!/usr/bin/env node
// Pushes an image/GIF to the Android companion (OpenPets IPC `pet.showMedia`).
//
// Sends the file bytes inline (base64), so the app does NOT need any network —
// only the existing IPC connection.
//
// usage:
//   node send-media.mjs <host> <port> <ipcToken> <filePath|url> ["caption"] [durationMs]
import net from "node:net";
import { randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";

const [host, portArg, token, source, caption, durationArg] = process.argv.slice(2);
if (!host || !portArg || !token || !source) {
  console.error('usage: send-media.mjs <host> <port> <ipcToken> <filePath|url> ["caption"] [durationMs]');
  process.exit(1);
}

const params = {};
if (/^https?:\/\//i.test(source)) {
  params.url = source;
} else {
  params.data = readFileSync(source).toString("base64");
}
if (caption) params.message = caption;
if (durationArg) params.durationMs = Number(durationArg);

const request = { id: randomUUID(), version: 1, token, method: "pet.showMedia", params };
const socket = net.createConnection({ host, port: Number(portArg) }, () => {
  socket.write(JSON.stringify(request) + "\n");
});
socket.setEncoding("utf8");
socket.setTimeout(10000, () => {
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
