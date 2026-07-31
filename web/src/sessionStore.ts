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
};
