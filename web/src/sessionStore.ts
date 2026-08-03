export type SessionInfo = {
  sessionId: string;
  token: string;
  displayName: string;
  expiresAtEpochSeconds: number;
  createdAtEpochSeconds: number;
};

const PREFIX = "dm.";

function key(name: string) {
  return PREFIX + name;
}

export function shortId(sessionId: string): string {
  return sessionId.length <= 8 ? sessionId : sessionId.slice(0, 8);
}

export function isExpired(info: SessionInfo, now = Math.floor(Date.now() / 1000)): boolean {
  if (!info.expiresAtEpochSeconds) return false;
  return now >= info.expiresAtEpochSeconds - 30;
}

export function relativeEpoch(epochSeconds?: number, now = Math.floor(Date.now() / 1000)): string {
  if (epochSeconds == null || epochSeconds <= 0) return "—";
  const d = Math.max(0, now - epochSeconds);
  if (d < 60) return `${d}s ago`;
  if (d < 3600) return `${Math.floor(d / 60)}m ago`;
  if (d < 86400) return `${Math.floor(d / 3600)}h ago`;
  return `${Math.floor(d / 86400)}d ago`;
}

/** Seconds until JWT expiry (0 if missing/expired). */
export function secondsUntilExpiry(info: SessionInfo, now = Math.floor(Date.now() / 1000)): number {
  if (!info.expiresAtEpochSeconds) return 0;
  return Math.max(0, info.expiresAtEpochSeconds - now);
}

export function formatTtl(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  const r = s % 60;
  if (m < 60) return r > 0 ? `${m}m ${r}s` : `${m}m`;
  const h = Math.floor(m / 60);
  const rm = m % 60;
  return rm > 0 ? `${h}h ${rm}m` : `${h}h`;
}

export const sessionStore = {
  loadBaseUrl(defaultUrl: string): string {
    return localStorage.getItem(key("base_url")) || defaultUrl;
  },
  saveBaseUrl(url: string) {
    localStorage.setItem(key("base_url"), url);
  },
  loadSession(): SessionInfo | null {
    const sessionId = localStorage.getItem(key("session_id"));
    const token = localStorage.getItem(key("token"));
    if (!sessionId || !token) return null;
    return {
      sessionId,
      token,
      displayName: localStorage.getItem(key("display_name")) || "Guest",
      expiresAtEpochSeconds: Number(localStorage.getItem(key("expires_at")) || 0),
      createdAtEpochSeconds: Number(localStorage.getItem(key("created_at")) || 0),
    };
  },
  saveSession(info: SessionInfo) {
    localStorage.setItem(key("session_id"), info.sessionId);
    localStorage.setItem(key("token"), info.token);
    localStorage.setItem(key("display_name"), info.displayName);
    localStorage.setItem(key("expires_at"), String(info.expiresAtEpochSeconds));
    localStorage.setItem(key("created_at"), String(info.createdAtEpochSeconds));
  },
  clearSession() {
    ["session_id", "token", "display_name", "expires_at", "created_at"].forEach((k) =>
      localStorage.removeItem(key(k)),
    );
  },
  loadAdminToken(): string {
    return localStorage.getItem(key("admin_token")) || "";
  },
  saveAdminToken(token: string) {
    if (!token) localStorage.removeItem(key("admin_token"));
    else localStorage.setItem(key("admin_token"), token);
  },
  loadMetricsToken(): string {
    return localStorage.getItem(key("metrics_token")) || "";
  },
  saveMetricsToken(token: string) {
    if (!token) localStorage.removeItem(key("metrics_token"));
    else localStorage.setItem(key("metrics_token"), token);
  },
  loadServerOpen(): boolean {
    return localStorage.getItem(key("server_open")) === "1";
  },
  saveServerOpen(open: boolean) {
    localStorage.setItem(key("server_open"), open ? "1" : "0");
  },
};
