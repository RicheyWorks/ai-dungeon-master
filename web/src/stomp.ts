export type StompListener = {
  onConnected: () => void;
  onMessage: (destination: string, body: string) => void;
  onError: (message: string) => void;
  onClosed: () => void;
  /** Fired when a reconnect attempt is scheduled (attempt is 1-based). */
  onReconnecting?: (attempt: number, delayMs: number) => void;
};

export type StompClientOptions = {
  /** Auto-reconnect after unexpected close (default true). */
  autoReconnect?: boolean;
  /** Max reconnect attempts (default 8). */
  maxReconnectAttempts?: number;
  /** Base delay ms for exponential backoff (default 800). */
  reconnectBaseMs?: number;
  /** Cap delay ms (default 15000). */
  reconnectMaxMs?: number;
};

/** Minimal STOMP 1.2 over native WebSocket (`/ws-stomp`). */
export class StompClient {
  private ws: WebSocket | null = null;
  private buffer = "";
  private subSeq = 0;
  private intentionalClose = false;
  private reconnectAttempt = 0;
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;
  /** Negotiated client→server heartbeat interval (0 = off). */
  private clientHeartbeatMs = 0;
  connected = false;

  private readonly autoReconnect: boolean;
  private readonly maxReconnectAttempts: number;
  private readonly reconnectBaseMs: number;
  private readonly reconnectMaxMs: number;

  constructor(
    private readonly url: string,
    private token: string | null,
    private readonly listener: StompListener,
    options: StompClientOptions = {},
  ) {
    this.autoReconnect = options.autoReconnect !== false;
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? 8;
    this.reconnectBaseMs = options.reconnectBaseMs ?? 800;
    this.reconnectMaxMs = options.reconnectMaxMs ?? 15_000;
  }

  static stompUrl(httpBase: string): string {
    let base = httpBase.replace(/\/$/, "");
    if (base.startsWith("https://")) base = "wss://" + base.slice("https://".length);
    else if (base.startsWith("http://")) base = "ws://" + base.slice("http://".length);
    else if (!(base.startsWith("ws://") || base.startsWith("wss://"))) base = "ws://" + base;
    // Empty base (same-origin via Vite proxy)
    if (base === "ws:" || base === "wss:") {
      const proto = location.protocol === "https:" ? "wss:" : "ws:";
      return `${proto}//${location.host}/ws-stomp`;
    }
    return base + "/ws-stomp";
  }

  /** Update Bearer token (e.g. after session refresh) before reconnect. */
  setToken(token: string | null) {
    this.token = token;
  }

  connect() {
    if (this.ws) return;
    this.intentionalClose = false;
    this.buffer = "";
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => this.sendRaw(this.buildConnect());
    this.ws.onmessage = (ev) => this.handleIncoming(String(ev.data));
    this.ws.onerror = () => this.listener.onError("WebSocket error");
    this.ws.onclose = () => {
      this.connected = false;
      this.ws = null;
      this.stopHeartbeat();
      this.buffer = "";
      this.listener.onClosed();
      this.scheduleReconnect();
    };
  }

  subscribe(destination: string): string {
    const id = `sub-${this.subSeq++}`;
    this.sendRaw(
      this.frame("SUBSCRIBE", { id, destination, ack: "auto" }, null),
    );
    return id;
  }

  send(destination: string, body: string) {
    this.sendRaw(
      this.frame(
        "SEND",
        {
          destination,
          "content-type": "application/json",
          "content-length": String(new TextEncoder().encode(body).length),
        },
        body,
      ),
    );
  }

  disconnect() {
    this.intentionalClose = true;
    this.clearReconnectTimer();
    this.stopHeartbeat();
    this.reconnectAttempt = 0;
    try {
      if (this.connected) this.sendRaw(this.frame("DISCONNECT", { receipt: "bye" }, null));
    } catch {
      /* ignore */
    }
    this.ws?.close();
    this.ws = null;
    this.connected = false;
    this.buffer = "";
  }

  private scheduleReconnect() {
    if (this.intentionalClose || !this.autoReconnect) return;
    if (this.reconnectAttempt >= this.maxReconnectAttempts) {
      this.listener.onError(`Live stream offline after ${this.maxReconnectAttempts} reconnects`);
      return;
    }
    this.reconnectAttempt += 1;
    const exp = Math.min(
      this.reconnectMaxMs,
      this.reconnectBaseMs * Math.pow(2, this.reconnectAttempt - 1),
    );
    const jitter = Math.floor(Math.random() * 200);
    const delay = exp + jitter;
    this.listener.onReconnecting?.(this.reconnectAttempt, delay);
    this.clearReconnectTimer();
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, delay);
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer != null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer != null) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    this.clientHeartbeatMs = 0;
  }

  private startHeartbeat(intervalMs: number) {
    this.stopHeartbeat();
    if (intervalMs <= 0) return;
    this.clientHeartbeatMs = intervalMs;
    // STOMP heartbeats are a single EOL (no frame command).
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send("\n");
      }
    }, intervalMs);
  }

  private sendRaw(frame: string) {
    this.ws?.send(frame);
  }

  private handleIncoming(chunk: string) {
    // Server heartbeats are lone newlines — ignore keep-alive noise.
    if (chunk === "\n" || chunk === "\r\n") return;
    this.buffer += chunk;
    while (true) {
      const idx = this.buffer.indexOf("\0");
      if (idx < 0) break;
      const raw = this.buffer.slice(0, idx);
      this.buffer = this.buffer.slice(idx + 1);
      if (raw.trim()) this.dispatch(raw);
    }
  }

  private dispatch(raw: string) {
    const normalized = raw.replace(/^[\n\r]+/, "");
    const parts = normalized.split("\n\n");
    const headerBlock = parts[0] ?? "";
    const body = parts.slice(1).join("\n\n");
    const lines = headerBlock.split("\n");
    const command = (lines[0] ?? "").trim();
    const headers: Record<string, string> = {};
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i];
      const colon = line.indexOf(":");
      if (colon > 0) headers[line.slice(0, colon)] = line.slice(colon + 1);
    }
    if (command === "CONNECTED") {
      this.connected = true;
      this.reconnectAttempt = 0;
      this.applyHeartbeatNegotiation(headers["heart-beat"] ?? headers["heartbeat"]);
      this.listener.onConnected();
    } else if (command === "MESSAGE") {
      this.listener.onMessage(headers.destination ?? "", body);
    } else if (command === "ERROR") {
      this.listener.onError(body || headers.message || "STOMP error");
    }
  }

  /**
   * STOMP 1.2 negotiation: client advertised cx,cy; server CONNECTED sx,sy.
   * Client must send every max(cx, sy) when both > 0.
   */
  private applyHeartbeatNegotiation(serverHb: string | undefined) {
    const clientCx = 10_000;
    const clientCy = 10_000;
    let sx = 0;
    let sy = 0;
    if (serverHb) {
      const parts = serverHb.split(",").map((s) => parseInt(s.trim(), 10));
      sx = Number.isFinite(parts[0]) ? parts[0]! : 0;
      sy = Number.isFinite(parts[1]) ? parts[1]! : 0;
    }
    const clientSend =
      clientCx > 0 && sy > 0 ? Math.max(clientCx, sy) : 0;
    this.startHeartbeat(clientSend);
    void clientCy;
    void sx;
  }

  private buildConnect(): string {
    const host = (() => {
      try {
        return new URL(this.url.replace(/^ws/, "http")).hostname || "localhost";
      } catch {
        return "localhost";
      }
    })();
    const headers: Record<string, string> = {
      "accept-version": "1.2,1.1,1.0",
      host,
      // Client can send every 10s; wants server every 10s (matches server defaults).
      "heart-beat": "10000,10000",
    };
    if (this.token) {
      headers.Authorization = `Bearer ${this.token}`;
      headers["X-Auth-Token"] = this.token;
    }
    return this.frame("CONNECT", headers, null);
  }

  private frame(command: string, headers: Record<string, string>, body: string | null): string {
    let s = command + "\n";
    for (const [k, v] of Object.entries(headers)) s += `${k}:${v}\n`;
    s += "\n";
    if (body != null) s += body;
    s += "\0";
    return s;
  }
}
