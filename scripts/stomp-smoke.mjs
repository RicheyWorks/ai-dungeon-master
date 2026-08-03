#!/usr/bin/env node
/**
 * STOMP ACL smoke against a running engine (native /ws-stomp).
 *
 * Requires auth-enabled engine (game.auth.enabled=true).
 *
 *   BASE_URL=http://127.0.0.1:8080 TOKEN=... SESSION_ID=... \
 *     node scripts/stomp-smoke.mjs
 *
 * Optional: OTHER_SESSION_ID (defaults to a fake id for cross-subscribe deny)
 *           STOMP_TIMEOUT_MS (default 8000)
 *
 * Checks:
 *   1) CONNECT with Bearer JWT → CONNECTED + session bound
 *   2) SUBSCRIBE /topic/narrative/{ownSessionId} → accepted (no ERROR)
 *   3) SUBSCRIBE /topic/narrative/{otherSessionId} → ERROR (cross-session deny)
 *   4) CONNECT without token → ERROR (auth required)
 *   5) SUBSCRIBE shared /topic/narrative (with JWT) → ERROR when auth on
 */
import process from "node:process";

const BASE_URL = (process.env.BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const TOKEN = process.env.TOKEN || "";
const SESSION_ID = process.env.SESSION_ID || "";
const OTHER_SESSION_ID = process.env.OTHER_SESSION_ID || "00000000-not-my-session";
const TIMEOUT_MS = Number(process.env.STOMP_TIMEOUT_MS || 8000);

const red = (s) => console.error(`\x1b[31m${s}\x1b[0m`);
const green = (s) => console.log(`\x1b[32m${s}\x1b[0m`);
const info = (s) => console.log(`→ ${s}`);

if (!TOKEN || !SESSION_ID) {
  red("TOKEN and SESSION_ID env vars are required");
  process.exit(2);
}
if (typeof WebSocket === "undefined") {
  red("Node WebSocket global required (Node 22+)");
  process.exit(2);
}

const wsUrl = BASE_URL.replace(/^http/i, "ws") + "/ws-stomp";

function frame(command, headers = {}, body = "") {
  let out = command + "\n";
  for (const [k, v] of Object.entries(headers)) {
    if (v == null) continue;
    out += `${k}:${String(v).replace(/[\r\n]/g, " ")}\n`;
  }
  out += "\n" + body + "\0";
  return out;
}

function parseFrames(text) {
  // Spring may batch frames; split on NUL.
  const parts = String(text).split("\0").filter((p) => p.length > 0);
  const frames = [];
  for (const part of parts) {
    const cleaned = part.replace(/^\n+/, "");
    if (!cleaned.trim()) continue;
    const nl = cleaned.indexOf("\n\n");
    const head = nl >= 0 ? cleaned.slice(0, nl) : cleaned;
    const body = nl >= 0 ? cleaned.slice(nl + 2) : "";
    const lines = head.split(/\n/);
    const command = lines[0].trim();
    const headers = {};
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      if (!line) continue;
      const c = line.indexOf(":");
      if (c < 0) continue;
      headers[line.slice(0, c)] = line.slice(c + 1);
    }
    frames.push({ command, headers, body });
  }
  return frames;
}

function openSocket() {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    const t = setTimeout(() => {
      try {
        ws.close();
      } catch {
        /* ignore */
      }
      reject(new Error(`WebSocket open timeout: ${wsUrl}`));
    }, TIMEOUT_MS);
    ws.addEventListener("open", () => {
      clearTimeout(t);
      resolve(ws);
    });
    ws.addEventListener("error", () => {
      clearTimeout(t);
      reject(new Error(`WebSocket error connecting to ${wsUrl}`));
    });
  });
}

/**
 * Send frames and collect inbound until predicate matches or timeout.
 * @returns {Promise<{frames: object[], ws: WebSocket}>}
 */
function exchange(ws, outboundFrames, { until, timeoutMs = TIMEOUT_MS } = {}) {
  return new Promise((resolve, reject) => {
    const collected = [];
    let done = false;
    const finish = (err) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      ws.removeEventListener("message", onMsg);
      ws.removeEventListener("close", onClose);
      ws.removeEventListener("error", onErr);
      if (err) reject(err);
      else resolve({ frames: collected, ws });
    };
    const onMsg = (ev) => {
      const text = typeof ev.data === "string" ? ev.data : Buffer.from(ev.data).toString("utf8");
      const frames = parseFrames(text);
      collected.push(...frames);
      if (until && until(collected, frames)) finish(null);
    };
    const onClose = () => {
      // Server may close after ERROR — still resolve so caller can inspect frames.
      finish(null);
    };
    const onErr = () => finish(new Error("WebSocket error during exchange"));
    const timer = setTimeout(() => finish(null), timeoutMs);
    ws.addEventListener("message", onMsg);
    ws.addEventListener("close", onClose);
    ws.addEventListener("error", onErr);
    for (const f of outboundFrames) ws.send(f);
  });
}

function hasCommand(frames, cmd) {
  return frames.some((f) => f.command === cmd);
}

function firstErrorMessage(frames) {
  const err = frames.find((f) => f.command === "ERROR");
  if (!err) return "";
  return (err.headers.message || err.headers["message"] || err.body || "").toString();
}

async function stepConnectWithJwt() {
  info("CONNECT with Bearer JWT");
  const ws = await openSocket();
  const { frames } = await exchange(
    ws,
    [
      frame("CONNECT", {
        "accept-version": "1.1,1.2",
        host: "localhost",
        "heart-beat": "0,0",
        Authorization: `Bearer ${TOKEN}`,
      }),
    ],
    { until: (all) => hasCommand(all, "CONNECTED") || hasCommand(all, "ERROR") },
  );
  if (!hasCommand(frames, "CONNECTED")) {
    red(`FAIL CONNECT JWT: expected CONNECTED got ${frames.map((f) => f.command).join(",") || "nothing"}`);
    if (firstErrorMessage(frames)) console.error(firstErrorMessage(frames));
    try {
      ws.close();
    } catch {
      /* ignore */
    }
    process.exit(1);
  }
  green("OK  STOMP CONNECTED (JWT)");
  return ws;
}

async function stepSubscribeOwn(ws) {
  info(`SUBSCRIBE own topic /topic/narrative/${SESSION_ID}`);
  const receiptId = "rcpt-own";
  const { frames } = await exchange(
    ws,
    [
      frame("SUBSCRIBE", {
        id: "sub-own",
        destination: `/topic/narrative/${SESSION_ID}`,
        ack: "auto",
        receipt: receiptId,
      }),
    ],
    {
      until: (all) =>
        hasCommand(all, "ERROR") ||
        all.some((f) => f.command === "RECEIPT" && f.headers["receipt-id"] === receiptId),
      timeoutMs: Math.min(TIMEOUT_MS, 4000),
    },
  );
  if (hasCommand(frames, "ERROR")) {
    red(`FAIL SUBSCRIBE own: ${firstErrorMessage(frames)}`);
    process.exit(1);
  }
  // RECEIPT is ideal; if broker is quiet, absence of ERROR within timeout is pass.
  green("OK  SUBSCRIBE own session topic");
}

async function stepSubscribeOther(ws) {
  info(`SUBSCRIBE foreign topic /topic/narrative/${OTHER_SESSION_ID}`);
  const { frames } = await exchange(
    ws,
    [
      frame("SUBSCRIBE", {
        id: "sub-other",
        destination: `/topic/narrative/${OTHER_SESSION_ID}`,
        ack: "auto",
      }),
    ],
    { until: (all) => hasCommand(all, "ERROR"), timeoutMs: Math.min(TIMEOUT_MS, 4000) },
  );
  if (!hasCommand(frames, "ERROR")) {
    red("FAIL SUBSCRIBE foreign: expected ERROR (cross-session ACL)");
    process.exit(1);
  }
  green(`OK  SUBSCRIBE foreign denied (${firstErrorMessage(frames).slice(0, 80) || "ERROR"})`);
}

async function stepSubscribeLegacyShared() {
  info("SUBSCRIBE shared /topic/narrative with JWT (should deny when auth on)");
  const ws = await openSocket();
  const connect = await exchange(
    ws,
    [
      frame("CONNECT", {
        "accept-version": "1.1,1.2",
        host: "localhost",
        "heart-beat": "0,0",
        Authorization: `Bearer ${TOKEN}`,
      }),
    ],
    { until: (all) => hasCommand(all, "CONNECTED") || hasCommand(all, "ERROR") },
  );
  if (!hasCommand(connect.frames, "CONNECTED")) {
    red("FAIL shared-topic setup: CONNECT failed");
    process.exit(1);
  }
  const { frames } = await exchange(
    ws,
    [
      frame("SUBSCRIBE", {
        id: "sub-legacy",
        destination: "/topic/narrative",
        ack: "auto",
      }),
    ],
    { until: (all) => hasCommand(all, "ERROR"), timeoutMs: Math.min(TIMEOUT_MS, 4000) },
  );
  try {
    ws.close();
  } catch {
    /* ignore */
  }
  if (!hasCommand(frames, "ERROR")) {
    red("FAIL SUBSCRIBE /topic/narrative: expected ERROR when auth required");
    process.exit(1);
  }
  green("OK  shared /topic/narrative denied when auth on");
}

async function stepConnectWithoutToken() {
  info("CONNECT without token (auth required)");
  const ws = await openSocket();
  const { frames } = await exchange(
    ws,
    [
      frame("CONNECT", {
        "accept-version": "1.1,1.2",
        host: "localhost",
        "heart-beat": "0,0",
      }),
    ],
    {
      until: (all) => hasCommand(all, "ERROR") || hasCommand(all, "CONNECTED"),
      timeoutMs: Math.min(TIMEOUT_MS, 4000),
    },
  );
  try {
    ws.close();
  } catch {
    /* ignore */
  }
  if (hasCommand(frames, "CONNECTED")) {
    red("FAIL anonymous CONNECT: expected ERROR when game.auth.enabled=true");
    process.exit(1);
  }
  if (!hasCommand(frames, "ERROR") && frames.length === 0) {
    // Some brokers close without ERROR frame; treat clean close as deny.
    green("OK  anonymous CONNECT rejected (connection closed)");
    return;
  }
  if (!hasCommand(frames, "ERROR")) {
    red(`FAIL anonymous CONNECT: expected ERROR got ${frames.map((f) => f.command).join(",")}`);
    process.exit(1);
  }
  green(`OK  anonymous CONNECT denied (${firstErrorMessage(frames).slice(0, 80) || "ERROR"})`);
}

async function main() {
  info(`STOMP smoke → ${wsUrl} session=${SESSION_ID}`);
  const ws = await stepConnectWithJwt();
  try {
    await stepSubscribeOwn(ws);
    await stepSubscribeOther(ws);
  } finally {
    try {
      ws.send(frame("DISCONNECT", { receipt: "bye" }));
    } catch {
      /* ignore */
    }
    try {
      ws.close();
    } catch {
      /* ignore */
    }
  }
  await stepSubscribeLegacyShared();
  await stepConnectWithoutToken();
  green("STOMP smoke PASSED");
}

main().catch((e) => {
  red(`FAIL STOMP smoke: ${e && e.message ? e.message : e}`);
  process.exit(1);
});
