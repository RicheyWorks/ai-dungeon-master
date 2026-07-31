export type StompListener = {
  onConnected: () => void;
  onMessage: (destination: string, body: string) => void;
  onError: (message: string) => void;
  onClosed: () => void;
};

/** Minimal STOMP 1.2 over native WebSocket (`/ws-stomp`). */
export class StompClient {
  private ws: WebSocket | null = null;
  private buffer = "";
  private subSeq = 0;
  connected = false;

  constructor(
    private readonly url: string,
    private readonly token: string | null,
    private readonly listener: StompListener,
  ) {}

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

  connect() {
    if (this.ws) return;
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => this.sendRaw(this.buildConnect());
    this.ws.onmessage = (ev) => this.handleIncoming(String(ev.data));
    this.ws.onerror = () => this.listener.onError("WebSocket error");
    this.ws.onclose = () => {
      this.connected = false;
      this.listener.onClosed();
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
    try {
      if (this.connected) this.sendRaw(this.frame("DISCONNECT", { receipt: "bye" }, null));
    } catch {
      /* ignore */
    }
    this.ws?.close();
    this.ws = null;
    this.connected = false;
  }

  private sendRaw(frame: string) {
    this.ws?.send(frame);
  }

  private handleIncoming(chunk: string) {
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
      this.listener.onConnected();
    } else if (command === "MESSAGE") {
      this.listener.onMessage(headers.destination ?? "", body);
    } else if (command === "ERROR") {
      this.listener.onError(body || headers.message || "STOMP error");
    }
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
      "heart-beat": "0,0",
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
