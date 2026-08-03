import { useCallback, useEffect, useRef, useState } from "react";
import type {
  AdminSessionRow,
  AdminSessionsPayload,
  CatalogPayload,
  EntitlementPayload,
  GameStatusV2,
  HealthPayload,
  MarketplaceInstallJob,
  MarketplacePayload,
  ReadinessResponse,
} from "./api";
import * as api from "./api";
import {
  DEV_STOREFRONT,
  KNOWN_STOREFRONTS,
  mintReceipt,
} from "./devReceipts";
import { steamBridge, steamReceipt } from "./steamPurchase";

import {
  isExpired,
  relativeEpoch,
  sessionStore,
  shortId,
  type SessionInfo,
} from "./sessionStore";
import { StompClient } from "./stomp";

const DEFAULT_BASE =
  typeof window !== "undefined" &&
  (window.location.port === "5173" || window.location.pathname.startsWith("/app"))
    ? "" // same origin (Vite proxy or engine-hosted /app)
    : "http://127.0.0.1:8080";

type Tab = "game" | "mods" | "store" | "system";

export function App() {
  const [baseUrl, setBaseUrl] = useState(() => sessionStore.loadBaseUrl(DEFAULT_BASE));
  const [session, setSession] = useState<SessionInfo | null>(() => {
    const s = sessionStore.loadSession();
    return s && !isExpired(s) ? s : null;
  });
  const [status, setStatus] = useState<GameStatusV2 | null>(null);
  const [catalog, setCatalog] = useState<CatalogPayload | null>(null);
  const [marketplace, setMarketplace] = useState<MarketplacePayload | null>(null);
  const [marketQuery, setMarketQuery] = useState("");
  const [installJob, setInstallJob] = useState<MarketplaceInstallJob | null>(null);
  const [entitlements, setEntitlements] = useState<EntitlementPayload | null>(null);
  const [narration, setNarration] = useState<string | null>(null);
  const [streamBuffer, setStreamBuffer] = useState("");
  const [stompConnected, setStompConnected] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(
    session ? `Restored session ${shortId(session.sessionId)} · ${session.displayName}` : null,
  );
  const [tab, setTab] = useState<Tab>("game");
  const [prompt, setPrompt] = useState("");
  const [productId, setProductId] = useState("sku_gold");
  /** Set when user taps Buy to unlock on a locked pack. */
  const [unlockHint, setUnlockHint] = useState<string | null>(null);
  const [storefront, setStorefront] = useState(DEV_STOREFRONT);
  const [receipt, setReceipt] = useState("");
  const [replace, setReplace] = useState(false);
  const [readiness, setReadiness] = useState<ReadinessResponse | null>(null);
  const [health, setHealth] = useState<HealthPayload | null>(null);
  const [healthOk, setHealthOk] = useState<boolean | null>(null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [healthAt, setHealthAt] = useState<string | null>(null);
  const [serverOpen, setServerOpen] = useState(() => sessionStore.loadServerOpen());
  const [copied, setCopied] = useState(false);
  const [dropActive, setDropActive] = useState(false);
  const [adminToken, setAdminToken] = useState(() => sessionStore.loadAdminToken());
  const [metricsToken, setMetricsToken] = useState(() => sessionStore.loadMetricsToken());
  const [adminSessions, setAdminSessions] = useState<AdminSessionsPayload | null>(null);
  const [metricsProbe, setMetricsProbe] = useState<{
    ok: boolean;
    status: number;
    bytes: number;
    sample?: string;
  } | null>(null);
  const [helpOpen, setHelpOpen] = useState(false);
  const stompRef = useRef<StompClient | null>(null);
  const mainRef = useRef<HTMLElement | null>(null);

  const token = session?.token ?? null;
  const installActive =
    !!installJob &&
    installJob.phase !== "DONE" &&
    installJob.phase !== "FAILED" &&
    installJob.phase !== "CANCELLED";
  const marketCount = marketplace?.available ?? null;
  const ownedCount = entitlements?.owned?.length ?? null;

  const pollHealth = useCallback(async (url = baseUrl) => {
    const ready = await api.fetchReadiness(url);
    const v2 = await api.fetchHealthV2(url, {
      adminToken: adminToken || undefined,
      metricsToken: metricsToken || undefined,
    });
    setReadiness(ready.body);
    setHealth(v2.payload);
    setHealthOk(ready.ok && v2.ok);
    setHealthError(ready.error ?? v2.error ?? null);
    setHealthAt(new Date().toLocaleTimeString());
  }, [baseUrl, adminToken, metricsToken]);

  useEffect(() => {
    void pollHealth();
    const id = window.setInterval(() => void pollHealth(), 15_000);
    return () => window.clearInterval(id);
  }, [pollHealth]);

  // Auto-dismiss informational banners (errors stay until dismissed).
  useEffect(() => {
    if (!info) return;
    const t = window.setTimeout(() => setInfo(null), 5500);
    return () => window.clearTimeout(t);
  }, [info]);

  // ? opens keyboard help (ignore when typing).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      const tag = t?.tagName?.toLowerCase();
      const inField =
        tag === "input" || tag === "textarea" || tag === "select" || t?.isContentEditable;
      if (inField) return;
      if (e.key === "?" || (e.shiftKey && e.key === "/")) {
        e.preventDefault();
        setHelpOpen((v) => !v);
      }
      if (e.key === "Escape") setHelpOpen(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const goTab = useCallback((next: Tab) => {
    setTab(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
    window.setTimeout(() => mainRef.current?.focus({ preventScroll: true }), 0);
  }, []);

  const disconnectStomp = useCallback(() => {
    stompRef.current?.disconnect();
    stompRef.current = null;
    setStompConnected(false);
  }, []);

  const connectStomp = useCallback(
    (s: SessionInfo) => {
      if (stompRef.current?.connected) return;
      disconnectStomp();
      const url = StompClient.stompUrl(baseUrl || window.location.origin);
      const client = new StompClient(url, s.token, {
        onConnected: () => {
          client.subscribe("/topic/narrative");
          client.subscribe(`/topic/narrative/${s.sessionId}`);
          setStompConnected(true);
          setInfo("Live stream connected");
        },
        onMessage: (_dest, body) => {
          const trimmed = body.trim();
          if (!trimmed) return;
          if (trimmed.startsWith("{")) {
            try {
              const env = JSON.parse(trimmed) as {
                type?: string;
                payload?: { chunk?: string; text?: string };
              };
              if (env.type === "narrative_chunk") {
                const chunk = env.payload?.chunk ?? env.payload?.text ?? "";
                setStreamBuffer((b) => b + chunk);
                setInfo(null);
                return;
              }
              if (env.type === "narrative_update") {
                setNarration(env.payload?.text ?? null);
                setStreamBuffer("");
                setInfo("Narration complete");
                return;
              }
            } catch {
              /* plain text */
            }
          }
          if (trimmed.startsWith("[WS]")) setInfo(trimmed);
          else setNarration((n) => (n ? n + "\n" + trimmed : trimmed));
        },
        onError: (msg) => {
          setStompConnected(false);
          setError(`WS: ${msg}`);
        },
        onClosed: () => setStompConnected(false),
      });
      stompRef.current = client;
      client.connect();
    },
    [baseUrl, disconnectStomp],
  );

  const ensureSession = useCallback(async (): Promise<SessionInfo> => {
    let candidate = session;
    if (!candidate || isExpired(candidate)) {
      const fromDisk = sessionStore.loadSession();
      if (fromDisk && !isExpired(fromDisk)) candidate = fromDisk;
    }
    if (candidate && !isExpired(candidate)) {
      const ok = await api.validateSession(baseUrl, candidate.token);
      if (ok) {
        sessionStore.saveSession(candidate);
        setSession(candidate);
        return candidate;
      }
    }
    sessionStore.clearSession();
    const fresh = await api.mintSession(baseUrl, candidate?.displayName);
    sessionStore.saveSession(fresh);
    setSession(fresh);
    setInfo(`New session ${shortId(fresh.sessionId)}`);
    return fresh;
  }, [baseUrl, session]);

  const run = useCallback(
    async (work: (s: SessionInfo) => Promise<void>) => {
      setBusy(true);
      setError(null);
      try {
        const s = await ensureSession();
        await work(s);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setBusy(false);
      }
    },
    [ensureSession],
  );

  const refresh = useCallback(() => {
    void run(async (s) => {
      connectStomp(s);
      setStatus(await api.getStatus(baseUrl, s.token));
    });
  }, [run, connectStomp, baseUrl]);

  useEffect(() => {
    refresh();
    return () => disconnectStomp();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onBaseUrlChange = (url: string) => {
    sessionStore.saveBaseUrl(url);
    if (url.replace(/\/$/, "") !== baseUrl.replace(/\/$/, "")) {
      disconnectStomp();
      sessionStore.clearSession();
      setSession(null);
      setStatus(null);
      setBaseUrl(url);
      setInfo("Server changed — new session on next sync");
    } else {
      setBaseUrl(url);
    }
  };

  const startSession = () => {
    void (async () => {
      setBusy(true);
      setError(null);
      try {
        disconnectStomp();
        sessionStore.clearSession();
        const fresh = await api.mintSession(baseUrl);
        sessionStore.saveSession(fresh);
        setSession(fresh);
        connectStomp(fresh);
        setInfo(`Session ${shortId(fresh.sessionId)} · ${fresh.displayName}`);
        setStatus(await api.getStatus(baseUrl, fresh.token));
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      } finally {
        setBusy(false);
      }
    })();
  };


  const copySessionId = () => {
    if (!session) return;
    void navigator.clipboard?.writeText(session.sessionId).then(
      () => {
        setCopied(true);
        setInfo(`Copied session ${shortId(session.sessionId)}`);
        window.setTimeout(() => setCopied(false), 1600);
      },
      () => setError("Clipboard unavailable"),
    );
  };

  const openMods = () => {
    goTab("mods");
    void (async () => {
      try {
        setMarketplace(await api.getMarketplace(baseUrl, token, marketQuery));
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
      if (token) {
        void run(async (s) => setCatalog(await api.getCatalog(baseUrl, s.token)));
      }
    })();
  };

  const openStore = () => {
    goTab("store");
    if (!entitlements) {
      void run(async (s) => setEntitlements(await api.listEntitlements(baseUrl, s.token)));
    }
  };

  return (
    <div className={`app${busy ? " is-busy" : ""}`}>
      <div className={`busy-bar${busy ? " on" : ""}`} aria-hidden />

      <header className="brand">
        <div className="brand-text">
          <h1>AI Dungeon Master</h1>
          <p className="tagline">Session-scoped play · live narration · content packs</p>
        </div>
        <div className="brand-actions">
          <button type="button" className="ghost compact" onClick={refresh} disabled={busy}>
            {busy ? "Syncing…" : "Sync"}
          </button>
          <button type="button" className="primary compact" onClick={startSession} disabled={busy}>
            {session ? "New session" : "Start session"}
          </button>
        </div>
      </header>

      <details
        className="server-details"
        open={serverOpen}
        onToggle={(e) => {
          const open = (e.target as HTMLDetailsElement).open;
          setServerOpen(open);
          sessionStore.saveServerOpen(open);
        }}
      >
        <summary>Server connection</summary>
        <div className="bar">
          <input
            value={baseUrl}
            onChange={(e) => onBaseUrlChange(e.target.value)}
            placeholder="Empty = same origin"
            spellCheck={false}
            aria-label="Engine base URL"
            autoComplete="off"
          />
          {session ? (
            <button
              type="button"
              className="ghost"
              disabled={busy}
              onClick={() =>
                void (async () => {
                  setBusy(true);
                  setError(null);
                  try {
                    if (session) {
                      await api.logoutSession(baseUrl, session.token);
                    }
                  } catch (e) {
                    console.warn(e);
                  } finally {
                    disconnectStomp();
                    sessionStore.clearSession();
                    setSession(null);
                    setCatalog(null);
                    setEntitlements(null);
                    setStatus(null);
                    setInfo("Logged out");
                    setBusy(false);
                  }
                })()
              }
            >
              Log out
            </button>
          ) : null}
        </div>
      </details>

      {session && (
        <div className={`session-line${stompConnected ? " live" : ""}`}>
          <span>
            {session.displayName} · {shortId(session.sessionId)}
            {stompConnected ? " · LIVE" : ""}
          </span>
          <button
            type="button"
            className="ghost compact"
            onClick={copySessionId}
            title="Copy full session id"
          >
            {copied ? "Copied" : "Copy id"}
          </button>
          <span
            className={
              healthOk === true ? "pill up" : healthOk === false ? "pill down" : "pill"
            }
            title={healthError ?? readiness?.status ?? "checking"}
          >
            {healthOk === true ? "READY" : healthOk === false ? "NOT READY" : "…"}
          </span>
          {installActive && tab !== "mods" ? (
            <button type="button" className="pill muted-pill job-chip" onClick={openMods}>
              Install {installJob?.percent ?? 0}%
            </button>
          ) : null}
        </div>
      )}
      {!session && healthOk !== null && (
        <div className="session-line">
          <span>Engine</span>
          <span className={healthOk ? "pill up" : "pill down"}>
            {healthOk ? "READY" : "NOT READY"}
          </span>
          {healthAt ? <span className="subtle">checked {healthAt}</span> : null}
        </div>
      )}

      {info && (
        <div className="banner" role="status">
          <span className="banner-text">{info}</span>
          <button type="button" className="banner-dismiss" onClick={() => setInfo(null)} aria-label="Dismiss">
            ×
          </button>
        </div>
      )}
      {error && (
        <div className="banner error" role="alert">
          <span className="banner-text">{error}</span>
          <button type="button" className="banner-dismiss" onClick={() => setError(null)} aria-label="Dismiss error">
            ×
          </button>
        </div>
      )}

      <nav className="tabs" aria-label="Main">
        <button
          type="button"
          className={tab === "game" ? "active" : ""}
          aria-current={tab === "game" ? "page" : undefined}
          onClick={() => goTab("game")}
        >
          Game
        </button>
        <button
          type="button"
          className={tab === "mods" ? "active" : ""}
          aria-current={tab === "mods" ? "page" : undefined}
          onClick={openMods}
        >
          Mods
          {marketCount != null ? <span className="tab-count">{marketCount}</span> : null}
        </button>
        <button
          type="button"
          className={tab === "store" ? "active" : ""}
          aria-current={tab === "store" ? "page" : undefined}
          onClick={openStore}
        >
          Store
          {ownedCount != null && ownedCount > 0 ? (
            <span className="tab-count">{ownedCount}</span>
          ) : null}
        </button>
        <button
          type="button"
          className={tab === "system" ? "active" : ""}
          aria-current={tab === "system" ? "page" : undefined}
          onClick={() => {
            goTab("system");
            void pollHealth();
          }}
        >
          System
        </button>
      </nav>

      <main id="main" className="main" ref={mainRef} tabIndex={-1}>
      {tab === "game" && (
        <GameTab
          status={status}
          busy={busy}
          stompConnected={stompConnected}
          narration={narration}
          streamBuffer={streamBuffer}
          prompt={prompt}
          setPrompt={setPrompt}
          onClearPrompt={() => setPrompt("")}
          onAct={(label) =>
            void run(async (s) => {
              connectStomp(s);
              if (stompRef.current?.connected) {
                stompRef.current.send("/app/action", JSON.stringify({ choiceLabel: label }));
                setStatus(await api.getStatus(baseUrl, s.token));
                setInfo("Action sent via WS");
              } else {
                setStatus(await api.submitAction(baseUrl, s.token, label));
              }
            })
          }
          onNarrate={() =>
            void run(async (s) => {
              connectStomp(s);
              if (stompRef.current?.connected) {
                setStreamBuffer("");
                setNarration(null);
                stompRef.current.send("/app/narrate", JSON.stringify({ prompt }));
                setInfo("Streaming narration…");
              } else {
                setNarration((await api.narrateRest(baseUrl, s.token, prompt)) ?? null);
                setInfo("REST narrate");
              }
            })
          }
          onSave={() =>
            void run(async (s) => {
              const p = await api.saveGame(baseUrl, s.token);
              setInfo(p.saved ? (p.sessionScoped ? "Saved (session)" : "Saved") : "Save failed");
            })
          }
          onLoad={() =>
            void run(async (s) => {
              setStatus(await api.loadGame(baseUrl, s.token));
              setInfo("Loaded save");
            })
          }
          onReset={() =>
            void run(async (s) => {
              setStatus(await api.resetGame(baseUrl, s.token));
              setInfo("New adventure started");
            })
          }
        />
      )}

      {tab === "mods" && (
        <ModsTab
          catalog={catalog}
          marketplace={marketplace}
          marketQuery={marketQuery}
          setMarketQuery={setMarketQuery}
          busy={busy}
          replace={replace}
          setReplace={setReplace}
          dropActive={dropActive}
          setDropActive={setDropActive}
          onReload={() =>
            void (async () => {
              try {
                setMarketplace(await api.getMarketplace(baseUrl, token, marketQuery));
                setInfo("Marketplace refreshed");
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
              if (token) {
                void run(async (s) => setCatalog(await api.getCatalog(baseUrl, s.token)));
              }
            })()
          }
          onSearch={() =>
            void (async () => {
              try {
                setMarketplace(await api.getMarketplace(baseUrl, token, marketQuery));
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
            })()
          }
          installJob={installJob}
          onInstall={(id) =>
            void (async () => {
              try {
                setError(null);
                const started = await api.startMarketplaceInstall(baseUrl, token, id);
                setInstallJob(started);
                setInfo(`Installing ${id}…`);
                const done = await api.pollMarketplaceInstall(
                  baseUrl,
                  token,
                  started.jobId,
                  (j) => setInstallJob(j),
                );
                setInstallJob(done);
                if (done.phase === "DONE") {
                  setInfo(done.message ?? `Installed ${id}`);
                  setMarketplace(await api.getMarketplace(baseUrl, token, marketQuery));
                  if (token) {
                    void run(async (s) => setCatalog(await api.getCatalog(baseUrl, s.token)));
                  }
                } else if (done.phase === "CANCELLED") {
                  setInfo(done.message ?? "Install cancelled");
                } else {
                  setError(done.error ?? done.message ?? "Install failed");
                }
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
            })()
          }
          onCancelInstall={() =>
            void (async () => {
              if (!installJob?.jobId) return;
              try {
                const j = await api.cancelMarketplaceInstall(baseUrl, token, installJob.jobId);
                if (j) setInstallJob(j);
                setInfo("Cancel requested");
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
            })()
          }
          onToggle={(id, enable) =>
            void run(async (s) => setCatalog(await api.togglePack(baseUrl, s.token, id, enable)))
          }
          onUpload={(file) =>
            void run(async (s) => {
              setCatalog(await api.uploadPack(baseUrl, s.token, file, replace));
              setMarketplace(await api.getMarketplace(baseUrl, s.token, marketQuery));
              setInfo(replace ? "Pack replaced" : "Pack uploaded");
            })
          }
          onBuyToUnlock={(sku, packLabel) => {
            setProductId(sku);
            setStorefront(DEV_STOREFRONT);
            setUnlockHint(
              packLabel
                ? `Unlock "${packLabel}" with product ${sku}`
                : `Unlock with product ${sku}`,
            );
            openStore();
            setInfo(`Store ready — buy ${sku} to unlock${packLabel ? ` ${packLabel}` : ""}.`);
          }}
        />
      )}

      {tab === "store" && (
        <StoreTab
          entitlements={entitlements}
          busy={busy}
          productId={productId}
          setProductId={setProductId}
          storefront={storefront}
          setStorefront={setStorefront}
          receipt={receipt}
          setReceipt={setReceipt}
          unlockHint={unlockHint}
          onClearUnlockHint={() => setUnlockHint(null)}
          onRefresh={() =>
            void run(async (s) => setEntitlements(await api.listEntitlements(baseUrl, s.token)))
          }
          onVerify={(override) =>
            void run(async (s) => {
              try {
                const p = await api.verifyReceipt(baseUrl, s.token, {
                  productId: override?.productId ?? productId,
                  receipt: override?.receipt ?? receipt,
                  storefront: (override?.storefront ?? storefront) || DEV_STOREFRONT,
                });
                setEntitlements(p);
                if (override?.receipt) setReceipt(override.receipt);
                if (override?.storefront) setStorefront(override.storefront);
                setInfo(p.granted ? `Granted ${p.productId}` : `Not granted: ${p.reason ?? ""}`);
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
                setEntitlements(await api.listEntitlements(baseUrl, s.token));
              }
            })
          }
          onSandboxBuy={(sku, sf) =>
            void run(async (s) => {
              const minted = await mintReceipt(sf || storefront, sku);
              const p = await api.verifyReceipt(baseUrl, s.token, {
                productId: minted.productId,
                receipt: minted.receipt,
                storefront: minted.storefront,
              });
              setEntitlements(p);
              setProductId(sku);
              setStorefront(minted.storefront);
              if (p.granted) setUnlockHint(null);
              const packs = (p as { enabledPacks?: string[] }).enabledPacks;
              setInfo(
                p.granted
                  ? packs && packs.length
                    ? `Granted ${p.productId}; enabled packs: ${packs.join(", ")}`
                    : `Sandbox ${minted.storefront} granted: ${p.productId}`
                  : `Failed: ${p.reason}`,
              );
              if (packs && packs.length) {
                void run(async (s) => setCatalog(await api.getCatalog(baseUrl, s.token)));
              }
            })
          }
        />
      )}

      {tab === "system" && (
        <SystemTab
          readiness={readiness}
          health={health}
          healthOk={healthOk}
          healthError={healthError}
          healthAt={healthAt}
          baseUrl={baseUrl}
          adminToken={adminToken}
          setAdminToken={(t) => {
            setAdminToken(t);
            sessionStore.saveAdminToken(t);
          }}
          metricsToken={metricsToken}
          setMetricsToken={(t) => {
            setMetricsToken(t);
            sessionStore.saveMetricsToken(t);
          }}
          adminSessions={adminSessions}
          metricsProbe={metricsProbe}
          busy={busy}
          currentSessionId={session?.sessionId ?? null}
          onRefresh={() => void pollHealth()}
          onLoadSessions={() =>
            void (async () => {
              try {
                setError(null);
                const p = await api.listAdminSessions(baseUrl, adminToken, 100);
                setAdminSessions(p);
                setInfo(`Loaded ${p.count ?? p.sessions?.length ?? 0} sessions`);
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
            })()
          }
          onRevokeSession={(id) =>
            void (async () => {
              try {
                setError(null);
                const r = await api.revokeAdminSession(baseUrl, adminToken, id);
                setInfo(
                  r.revoked
                    ? `Revoked ${id.slice(0, 8)}…${r.existed === false ? " (was unknown)" : ""}`
                    : "Revoke failed",
                );
                setAdminSessions(await api.listAdminSessions(baseUrl, adminToken, 100));
                if (session?.sessionId === id) {
                  disconnectStomp();
                  sessionStore.clearSession();
                  setSession(null);
                  setStatus(null);
                }
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
            })()
          }
          onProbeMetrics={() =>
            void (async () => {
              try {
                const r = await api.probeMetrics(baseUrl, metricsToken || undefined);
                setMetricsProbe(r);
                setInfo(r.ok ? `Metrics OK (${r.bytes} B)` : `Metrics HTTP ${r.status}`);
              } catch (e) {
                setError(e instanceof Error ? e.message : String(e));
              }
            })()
          }
          onCopyBase={() => {
            const u = baseUrl.trim() || window.location.origin;
            void navigator.clipboard?.writeText(u).then(
              () => setInfo(`Copied ${u}`),
              () => setError("Clipboard unavailable"),
            );
          }}
        />
      )}


      </main>

      <footer className="app-footer">
        <span className="subtle">
          Game: keys <kbd>1</kbd>–<kbd>9</kbd> choose · <kbd>Ctrl</kbd>+<kbd>Enter</kbd> narrate ·{" "}
          <button type="button" className="linkish" onClick={() => setHelpOpen(true)}>
            ?
          </button>{" "}
          help
        </span>
        <span className="subtle">SPA · /app</span>
      </footer>

      {helpOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setHelpOpen(false)}>
          <div
            className="modal card stack"
            role="dialog"
            aria-modal="true"
            aria-label="Keyboard shortcuts"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="row between">
              <h3>Keyboard shortcuts</h3>
              <button type="button" className="ghost compact" onClick={() => setHelpOpen(false)}>
                Close
              </button>
            </div>
            <ul className="help-list">
              <li>
                <kbd>1</kbd>–<kbd>9</kbd> — pick a choice (Game tab)
              </li>
              <li>
                <kbd>Ctrl</kbd>/<kbd>⌘</kbd>+<kbd>Enter</kbd> — send narrate
              </li>
              <li>
                <kbd>?</kbd> — toggle this help
              </li>
              <li>
                <kbd>Esc</kbd> — close dialogs
              </li>
            </ul>
            <p className="muted" style={{ margin: 0 }}>
              Ops: System tab stores admin / metrics tokens locally (this browser only) for health
              detail, session list/revoke, and metrics probe.
            </p>
          </div>
        </div>
      ) : null}
    </div>
  );
}


function GameTab(props: {
  status: GameStatusV2 | null;
  busy: boolean;
  stompConnected: boolean;
  narration: string | null;
  streamBuffer: string;
  prompt: string;
  setPrompt: (v: string) => void;
  onAct: (label: string) => void;
  onNarrate: () => void;
  onClearPrompt: () => void;
  onSave: () => void;
  onLoad: () => void;
  onReset: () => void;
}) {
  const { status } = props;
  const quest = status?.quest;
  const progress = Math.min(Math.max(quest?.progress ?? 0, 0), 1);
  const outcome =
    quest?.completed
      ? "Completed"
      : quest?.failed
        ? "Failed"
        : status?.combatActive
          ? "In combat"
          : "In progress";
  const choices = status?.availableChoices ?? [];
  const choiceSignature = choices.join("\u0001");
  const rifts = status?.discoveredRifts ?? [];
  const history = status?.recentHistory ?? [];
  const streamRef = useRef<HTMLDivElement | null>(null);
  const promptRef = useRef<HTMLTextAreaElement | null>(null);

  // Number keys 1–9 choose; Ctrl/Cmd+Enter narrates when prompt focused.
  useEffect(() => {
    const list = choiceSignature ? choiceSignature.split("\u0001") : [];
    const onKey = (e: KeyboardEvent) => {
      if (props.busy) return;
      const t = e.target as HTMLElement | null;
      const tag = t?.tagName?.toLowerCase();
      const inField =
        tag === "input" ||
        tag === "textarea" ||
        tag === "select" ||
        t?.isContentEditable === true;

      if ((e.metaKey || e.ctrlKey) && e.key === "Enter") {
        if (props.prompt.trim()) {
          e.preventDefault();
          props.onNarrate();
        }
        return;
      }

      if (inField) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;

      const n = Number(e.key);
      if (n >= 1 && n <= 9 && list[n - 1]) {
        e.preventDefault();
        props.onAct(list[n - 1]);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [props.busy, props.prompt, props.onAct, props.onNarrate, choiceSignature]);

  useEffect(() => {
    const el = streamRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [props.streamBuffer, props.narration]);

  return (
    <div className="stack game-tab">
      <div className="toolbar-sticky row">
        <button type="button" onClick={props.onSave} disabled={props.busy}>
          Save
        </button>
        <button type="button" onClick={props.onLoad} disabled={props.busy}>
          Load
        </button>
        <button type="button" className="ghost" onClick={props.onReset} disabled={props.busy}>
          Reset
        </button>
        {props.stompConnected ? (
          <span className="pill up" title="STOMP live stream">
            LIVE
          </span>
        ) : (
          <span className="pill muted-pill" title="REST fallback for narrate/action">
            REST
          </span>
        )}
        {status?.combatActive ? <span className="pill down">COMBAT</span> : null}
      </div>

      {!status && props.busy ? (
        <div className="stack" aria-busy="true" aria-label="Loading adventure">
          <div className="skeleton sk-title" />
          <div className="skeleton sk-block" />
          <div className="party-grid">
            <div className="skeleton sk-card" />
            <div className="skeleton sk-card" />
          </div>
        </div>
      ) : null}

      {!status && !props.busy ? (
        <div className="empty">
          <strong>No adventure yet</strong>
          Start or restore a session, then Sync to load party and choices.
        </div>
      ) : null}

      {status ? (
        <>
          <div className={`card quest-card${status.combatActive ? " combat" : ""}`}>
            <h2>{quest?.title ?? "No active quest"}</h2>
            <div className="quest-meta muted">
              <span
                className={
                  status.combatActive
                    ? "pill down"
                    : quest?.completed
                      ? "pill up"
                      : quest?.failed
                        ? "pill down"
                        : "pill muted-pill"
                }
              >
                {outcome}
              </span>
              <span className="stat">
                Chaos <b>{status.chaosLevel ?? "?"}</b>
              </span>
              {status.location ? (
                <span className="stat">
                  At <b>{status.location}</b>
                </span>
              ) : null}
            </div>
            <div
              className="progress"
              role="progressbar"
              aria-valuenow={Math.round(progress * 100)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Quest progress"
            >
              <span style={{ width: `${progress * 100}%` }} />
            </div>
            <div className="subtle" style={{ marginTop: 6 }}>
              Quest progress {Math.round(progress * 100)}%
            </div>
            {rifts.length > 0 && (
              <div className="rift-row">
                <span className="subtle">Discovered rifts</span>
                <div className="row" style={{ marginTop: 6 }}>
                  {rifts.map((r) => (
                    <span key={r} className="pill muted-pill" title={r}>
                      {r}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>

          <section>
            <h3>Party</h3>
            {(status.party ?? []).length === 0 ? (
              <div className="empty">No party members on this status snapshot.</div>
            ) : (
              <div className="party-grid">
                {(status.party ?? []).map((m, i) => {
                  const hp = Math.max(m.hp ?? 0, 0);
                  const maxHp = Math.max(m.maxHp ?? 1, 1);
                  const ratio = hp / maxHp;
                  const low = ratio <= 0.35 && m.alive !== false;
                  return (
                    <div
                      className={`card party-card${m.alive === false ? " is-fallen" : ""}${low ? " is-low" : ""}`}
                      key={i}
                    >
                      <div className="row between">
                        <span className="name">{m.name ?? "?"}</span>
                        <span className="muted">
                          {m.role ?? ""} · L{m.level ?? 1}
                        </span>
                      </div>
                      <div
                        className="progress hp"
                        role="progressbar"
                        aria-valuenow={hp}
                        aria-valuemin={0}
                        aria-valuemax={maxHp}
                        aria-label={`${m.name ?? "Member"} hit points`}
                      >
                        <span style={{ width: `${ratio * 100}%` }} />
                      </div>
                      <div className="muted" style={{ marginTop: 6 }}>
                        HP {hp}/{maxHp}
                        {m.mana != null ? ` · MP ${m.mana}/${m.maxMana ?? m.mana}` : ""}
                        {m.alive === false ? <span className="fallen"> · FALLEN</span> : null}
                        {(m.statuses ?? []).length
                          ? ` · ${(m.statuses ?? []).join(", ")}`
                          : ""}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          {(status.recentEvents ?? []).length > 0 && (
            <section>
              <h3>Story so far</h3>
              <div className="card chronicle">
                {(status.recentEvents ?? []).map((e, i) => (
                  <div key={i} className="chronicle-line">
                    {e}
                  </div>
                ))}
              </div>
            </section>
          )}

          {history.length > 0 && (
            <details className="card history-fold">
              <summary>Engine log ({history.length})</summary>
              <div className="history-body">
                {history.map((line, i) => (
                  <div key={i} className="subtle">
                    {line}
                  </div>
                ))}
              </div>
            </details>
          )}

          <section>
            <div className="section-head">
              <h3>Choices</h3>
              {choices.length > 0 ? (
                <span className="subtle">Keys 1–{Math.min(9, choices.length)}</span>
              ) : null}
            </div>
            {choices.length === 0 ? (
              <div className="empty">
                <strong>No choices right now</strong>
                Narrate below or wait for the next beat.
              </div>
            ) : (
              choices.map((label, idx) => (
                <button
                  key={label}
                  type="button"
                  className="choice primary"
                  disabled={props.busy}
                  onClick={() => props.onAct(label)}
                >
                  <span className="choice-key" aria-hidden>
                    {idx < 9 ? idx + 1 : "·"}
                  </span>
                  <span className="choice-label">{label}</span>
                </button>
              ))
            )}
          </section>
        </>
      ) : null}

      <section className="narrate-panel">
        <h3>
          {props.stompConnected ? "Ask the Dungeon Master (live)" : "Ask the Dungeon Master"}
        </h3>
        <textarea
          ref={promptRef}
          rows={3}
          value={props.prompt}
          onChange={(e) => props.setPrompt(e.target.value)}
          onKeyDown={(e) => {
            if ((e.metaKey || e.ctrlKey) && e.key === "Enter" && props.prompt.trim()) {
              e.preventDefault();
              props.onNarrate();
            }
          }}
          placeholder="What do you do? (Ctrl/⌘+Enter to send)"
          aria-label="Narration prompt"
        />
        <div className="row" style={{ marginTop: 8 }}>
          <button
            type="button"
            className="primary"
            disabled={props.busy || !props.prompt.trim()}
            onClick={props.onNarrate}
          >
            {props.stompConnected ? "Stream narrate" : "Narrate"}
          </button>
          {props.prompt ? (
            <button type="button" className="ghost" onClick={props.onClearPrompt}>
              Clear
            </button>
          ) : null}
          {props.streamBuffer ? (
            <span className="pill muted-pill">Streaming…</span>
          ) : null}
        </div>
        {(props.streamBuffer || props.narration) && (
          <div className="card stream-panel" ref={streamRef}>
            {props.streamBuffer ? (
              <div className="stream">{props.streamBuffer}</div>
            ) : null}
            {props.narration && !props.streamBuffer ? (
              <div className="narration">{props.narration}</div>
            ) : null}
            {props.narration && props.streamBuffer ? (
              <div className="narration" style={{ marginTop: 12, opacity: 0.85 }}>
                {props.narration}
              </div>
            ) : null}
          </div>
        )}
      </section>
    </div>
  );
}

function ModsTab(props: {
  catalog: CatalogPayload | null;
  marketplace: MarketplacePayload | null;
  marketQuery: string;
  setMarketQuery: (v: string) => void;
  busy: boolean;
  replace: boolean;
  setReplace: (v: boolean) => void;
  dropActive: boolean;
  setDropActive: (v: boolean) => void;
  installJob: MarketplaceInstallJob | null;
  onReload: () => void;
  onSearch: () => void;
  onInstall: (id: string) => void;
  onCancelInstall: () => void;
  onToggle: (id: string, enable: boolean) => void;
  onUpload: (file: File) => void;
  onBuyToUnlock: (sku: string, packLabel?: string) => void;
}) {
  const marketPacks = props.marketplace?.packs ?? [];
  const livePacks = props.catalog?.contentPacks ?? [];
  const job = props.installJob;
  const jobActive =
    !!job &&
    job.phase !== "DONE" &&
    job.phase !== "FAILED" &&
    job.phase !== "CANCELLED";
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const acceptFile = (file: File | undefined | null) => {
    if (!file) return;
    const ok =
      file.name.toLowerCase().endsWith(".zip") || file.type === "application/zip";
    if (!ok) return;
    props.onUpload(file);
  };

  return (
    <div className="stack">
      <div className="section-head">
        <h3>Marketplace</h3>
        <button type="button" onClick={props.onReload} disabled={props.busy || jobActive}>
          Reload
        </button>
      </div>
      {job && (
        <div className="card stack">
          <div className="row between">
            <strong>
              Install {job.packId ?? "…"} · {job.phase ?? "…"}
            </strong>
            {jobActive && (
              <button type="button" className="ghost" onClick={props.onCancelInstall}>
                Cancel
              </button>
            )}
          </div>
          <div
            className="progress job"
            role="progressbar"
            aria-valuenow={job.percent ?? 0}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Install progress"
          >
            <span style={{ width: `${Math.min(100, Math.max(0, job.percent ?? 0))}%` }} />
          </div>
          <div className="muted">
            {job.percent ?? 0}%
            {job.bytesTotal && job.bytesTotal > 0
              ? ` · ${job.bytesRead ?? 0} / ${job.bytesTotal} bytes`
              : ""}
            {job.message ? ` · ${job.message}` : ""}
          </div>
        </div>
      )}
      <p className="muted">
        Discovery via <code>/v2/marketplace</code>
        {props.marketplace?.root ? ` · ${props.marketplace.root}` : ""}.{" "}
        {props.marketplace
          ? `${props.marketplace.available ?? 0} available · ${props.marketplace.installed ?? 0} installed`
          : "Open this tab to load packs."}
        {props.marketplace?.remoteIndexUrl ? (
          <>
            {" · remote "}
            <span className={props.marketplace.remoteOk ? "pill up" : "pill down"}>
              {props.marketplace.remoteOk ? "OK" : "ERR"}
            </span>
            {props.marketplace.remoteError ? ` · ${props.marketplace.remoteError}` : ""}
          </>
        ) : null}
      </p>

      <div className="toolbar">
        <input
          value={props.marketQuery}
          onChange={(e) => props.setMarketQuery(e.target.value)}
          placeholder="Search packs…"
          aria-label="Search marketplace"
          onKeyDown={(e) => {
            if (e.key === "Enter") props.onSearch();
          }}
        />
        <button type="button" onClick={props.onSearch} disabled={props.busy}>
          Search
        </button>
      </div>

      {marketPacks.length === 0 && props.marketplace && (
        <div className="empty">
          <strong>No packs match</strong>
          Try a different search or reload the marketplace.
        </div>
      )}

      {!props.marketplace && (
        <div className="empty">
          <strong>Marketplace not loaded</strong>
          Hit Reload to fetch available content packs.
        </div>
      )}

      {marketPacks.map((pack) => (
        <div className="card stack" key={pack.id}>
          <div className="row between">
            <div>
              <div className="pack-title">
                {pack.displayName ?? pack.id}{" "}
                <span
                  className={
                    pack.source === "remote" ? "pill muted-pill" : "pill up"
                  }
                  title={pack.downloadUrl ?? pack.sourcePath ?? pack.source}
                >
                  {(pack.source ?? "local").toUpperCase()}
                </span>
              </div>
              <div className="muted">
                v{pack.version ?? "?"} · min engine {pack.minEngineVersion ?? "?"}
                {pack.installed ? " · installed" : " · not installed"}
                {pack.enabled ? " · enabled" : ""}
              </div>
              {pack.source === "remote" && pack.downloadUrl && (
                <div className="subtle" style={{ wordBreak: "break-all" }}>
                  {pack.downloadUrl}
                </div>
              )}
              {pack.sha256 && (
                <div className="subtle" title={pack.sha256}>
                  sha256 {pack.sha256.slice(0, 12)}…{pack.sha256.slice(-8)}
                  {pack.source === "remote" ? " · verified on install" : ""}
                </div>
              )}
            </div>
            {!pack.installed ? (
              <button
                type="button"
                className="primary"
                disabled={props.busy || jobActive}
                onClick={() => props.onInstall(pack.id)}
              >
                {jobActive && job?.packId === pack.id ? "Installing…" : "Install"}
              </button>
            ) : (
              <span className="pill up">Installed</span>
            )}
          </div>
          {pack.description && <p className="muted" style={{ margin: 0 }}>{pack.description}</p>}
        </div>
      ))}

      <div className="section-head" style={{ marginTop: "0.5rem" }}>
        <h3>Live catalog</h3>
      </div>

      <div className="card stack">
        <strong>Upload pack zip</strong>
        <p className="muted" style={{ margin: 0 }}>
          Multipart <code>POST /v2/catalog/packs</code>
          {` — may require admin token in multi-tenant prod.`}
        </p>
        <label className="switch">
          <span>Replace if exists</span>
          <input
            type="checkbox"
            checked={props.replace}
            onChange={(e) => props.setReplace(e.target.checked)}
            disabled={props.busy}
          />
        </label>
        <div
          className={`dropzone${props.dropActive ? " active" : ""}${props.busy ? " disabled" : ""}`}
          onDragEnter={(e) => {
            e.preventDefault();
            if (!props.busy) props.setDropActive(true);
          }}
          onDragOver={(e) => e.preventDefault()}
          onDragLeave={() => props.setDropActive(false)}
          onDrop={(e) => {
            e.preventDefault();
            props.setDropActive(false);
            if (!props.busy) acceptFile(e.dataTransfer.files?.[0]);
          }}
          onClick={() => !props.busy && fileInputRef.current?.click()}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              fileInputRef.current?.click();
            }
          }}
        >
          <strong>Drop a .zip here</strong>
          <span className="subtle">or click to browse</span>
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept=".zip,application/zip"
          disabled={props.busy}
          className="sr-only"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) props.onUpload(f);
            e.target.value = "";
          }}
        />
      </div>

      {livePacks.length === 0 && (
        <div className="empty">
          <strong>No live packs</strong>
          Install from the marketplace or upload a zip.
        </div>
      )}

      {livePacks.map((pack) => (
        <div className="card switch" key={pack.id ?? pack.displayName}>
          <div>
            <div className="pack-title">
              {pack.displayName ?? pack.id ?? "?"}
              {pack.locked ? (
                <span className="pill muted-pill" title={(pack.requiredProductIds ?? []).join(", ")}>
                  LOCKED
                </span>
              ) : null}
            </div>
            <div className="muted">
              v{pack.version ?? "?"} · {pack.monsters ?? 0} monsters · {pack.items ?? 0} items
              {pack.requiredProductIds && pack.requiredProductIds.length
                ? ` · requires ${pack.requiredProductIds.join(" | ")}`
                : ""}
            </div>
          </div>
          <div className="stack" style={{ alignItems: "flex-end", gap: 6 }}>
            <input
              type="checkbox"
              checked={pack.enabled === true}
              disabled={props.busy || !pack.id || (!!pack.locked && !pack.enabled)}
              onChange={(e) => pack.id && props.onToggle(pack.id, e.target.checked)}
              aria-label={`Enable ${pack.displayName ?? pack.id}`}
            />
            {pack.locked && (pack.requiredProductIds?.length ?? 0) > 0 ? (
              <button
                type="button"
                className="primary"
                disabled={props.busy}
                onClick={() =>
                  props.onBuyToUnlock(
                    pack.requiredProductIds![0],
                    pack.displayName ?? pack.id ?? undefined,
                  )
                }
              >
                Buy to unlock
              </button>
            ) : null}
          </div>
        </div>
      ))}

      {props.catalog?.narration && (
        <div className="card">
          <h3>Narration</h3>
          <div>
            {props.catalog.narration.active ?? "?"} ({props.catalog.narration.health ?? "UNKNOWN"})
          </div>
          <div className="muted">
            Available: {(props.catalog.narration.available ?? []).join(", ")}
          </div>
        </div>
      )}
    </div>
  );
}

function StoreTab(props: {
  entitlements: EntitlementPayload | null;
  busy: boolean;
  productId: string;
  setProductId: (v: string) => void;
  storefront: string;
  setStorefront: (v: string) => void;
  receipt: string;
  setReceipt: (v: string) => void;
  unlockHint: string | null;
  onClearUnlockHint: () => void;
  onRefresh: () => void;
  onVerify: (override?: { productId?: string; receipt?: string; storefront?: string }) => void;
  onSandboxBuy: (sku: string, storefront: string) => void;
}) {
  const owned = props.entitlements?.owned ?? [];
  const demos = ["sku_gold", "sku_season_pass", "pack_the_hollows"];

  return (
    <div className="stack">
      <div className="section-head">
        <h3>Entitlements</h3>
        <button type="button" onClick={props.onRefresh} disabled={props.busy}>
          Refresh
        </button>
      </div>

      <div className="card">
        <strong>Owned products</strong>
        {owned.length === 0 ? (
          <p className="muted">None yet — buy via Steam/desktop, sandbox, or verify a receipt.</p>
        ) : (
          <div className="chip-row">
            {owned.map((sku) => (
              <span key={sku} className="chip">
                {sku}
              </span>
            ))}
          </div>
        )}
        {props.entitlements?.reason && (
          <div className="muted">Last: {props.entitlements.reason}</div>
        )}
      </div>

      {props.unlockHint ? (
        <div className="card stack" style={{ borderColor: "var(--border-strong)" }}>
          <strong>Unlock pack</strong>
          <p className="muted" style={{ margin: 0 }}>{props.unlockHint}</p>
          <div className="row">
            <button
              type="button"
              className="primary"
              disabled={props.busy || !props.productId.trim()}
              onClick={() => {
                props.onSandboxBuy(props.productId.trim(), props.storefront || DEV_STOREFRONT);
              }}
            >
              Buy {props.productId} (sandbox)
            </button>
            <button type="button" className="ghost" disabled={props.busy} onClick={props.onClearUnlockHint}>
              Dismiss
            </button>
          </div>
        </div>
      ) : null}

      <div className="card stack">
        <strong>Steam desktop (orderId)</strong>
        <p className="muted" style={{ margin: 0 }}>
          Steamworks MicroTxn: paste orderId from InitTxn, then verify storefront{" "}
          <code>steam</code>. See <code>desktop/STEAM.md</code>.
        </p>
        <SteamOrderPanel
          busy={props.busy}
          productId={props.productId}
          setProductId={props.setProductId}
          setStorefront={props.setStorefront}
          setReceipt={props.setReceipt}
          onVerify={props.onVerify}
        />
      </div>

      <div className="card stack">
        <strong>Sandbox purchase</strong>
        <p className="muted" style={{ margin: 0 }}>
          Mints a storefront-shaped receipt and posts it to{" "}
          <code>POST /v2/entitlements/verify</code>.
        </p>
        <div className="row">
          {KNOWN_STOREFRONTS.map((id) => (
            <button
              key={id}
              type="button"
              className={props.storefront === id ? "primary" : ""}
              disabled={props.busy}
              onClick={() => props.setStorefront(id)}
            >
              {id}
            </button>
          ))}
        </div>
        <input
          value={props.productId}
          onChange={(e) => props.setProductId(e.target.value)}
          aria-label="Product id"
        />
        <button
          type="button"
          className="primary"
          disabled={props.busy || !props.productId.trim()}
          onClick={() => props.onSandboxBuy(props.productId.trim(), props.storefront)}
        >
          Buy with {props.storefront} sandbox
        </button>
      </div>

      <div className="card stack">
        <strong>Verify arbitrary receipt</strong>
        <input
          value={props.storefront}
          onChange={(e) => props.setStorefront(e.target.value)}
          placeholder="Storefront"
        />
        <input
          value={props.productId}
          onChange={(e) => props.setProductId(e.target.value)}
          placeholder="Product id"
        />
        <textarea
          rows={2}
          value={props.receipt}
          onChange={(e) => props.setReceipt(e.target.value)}
          placeholder="Receipt"
        />
        <button
          type="button"
          className="primary"
          disabled={props.busy || !props.productId.trim() || !props.receipt.trim()}
          onClick={() => props.onVerify()}
        >
          Verify receipt
        </button>
      </div>

      <section>
        <h3>Demo SKUs</h3>
        {demos.map((sku) => (
          <button
            key={sku}
            type="button"
            className="sku-btn"
            disabled={props.busy}
            onClick={() => props.onSandboxBuy(sku, props.storefront)}
          >
            Buy {sku} ({props.storefront})
          </button>
        ))}
      </section>
    </div>
  );
}


function SteamOrderPanel(props: {
  busy: boolean;
  productId: string;
  setProductId: (v: string) => void;
  setStorefront: (v: string) => void;
  setReceipt: (v: string) => void;
  onVerify: (override?: { productId?: string; receipt?: string; storefront?: string }) => void;
}) {
  const [steamOrderId, setSteamOrderId] = useState("");
  const [steamId, setSteamId] = useState("76561198000000000");
  const [note, setNote] = useState<string | null>(null);
  const fill = () => {
    const r = steamReceipt({
      orderId: steamOrderId,
      productId: props.productId,
      steamId,
    });
    props.setStorefront("steam");
    props.setReceipt(r);
    return r;
  };
  return (
    <>
      <input
        value={steamOrderId}
        onChange={(e) => setSteamOrderId(e.target.value)}
        placeholder="Steam orderId"
      />
      <input
        value={steamId}
        onChange={(e) => setSteamId(e.target.value)}
        placeholder="SteamID64 (optional)"
      />
      <input
        value={props.productId}
        onChange={(e) => props.setProductId(e.target.value)}
        placeholder="Product id"
      />
      <div className="row">
        <button
          type="button"
          className="primary"
          disabled={props.busy || !steamOrderId.trim() || !props.productId.trim()}
          onClick={() => {
            fill();
            setNote("Receipt filled — click Verify receipt below.");
          }}
        >
          Fill steam receipt
        </button>
        <button
          type="button"
          disabled={props.busy || !steamOrderId.trim() || !props.productId.trim()}
          onClick={() => {
            const r = fill();
            props.onVerify({ productId: props.productId.trim(), receipt: r, storefront: "steam" });
          }}
        >
          Verify steam order
        </button>
        <button
          type="button"
          disabled={props.busy || !props.productId.trim() || !steamBridge()?.initPurchase}
          onClick={() => {
            const bridge = steamBridge();
            if (!bridge?.initPurchase) {
              setNote("No window.__dmSteam.initPurchase");
              return;
            }
            void (async () => {
              try {
                const orderId = await bridge.initPurchase!(props.productId.trim());
                setSteamOrderId(orderId);
                if (bridge.getSteamId) {
                  const id = await bridge.getSteamId();
                  if (id) setSteamId(id);
                }
                setNote(`InitTxn orderId=${orderId}`);
              } catch (e) {
                setNote(e instanceof Error ? e.message : String(e));
              }
            })();
          }}
        >
          Init via __dmSteam
        </button>
      </div>
      {note && <div className="muted">{note}</div>}
    </>
  );
}

function SystemTab(props: {
  readiness: ReadinessResponse | null;
  health: HealthPayload | null;
  healthOk: boolean | null;
  healthError: string | null;
  healthAt: string | null;
  baseUrl: string;
  adminToken: string;
  setAdminToken: (v: string) => void;
  metricsToken: string;
  setMetricsToken: (v: string) => void;
  adminSessions: AdminSessionsPayload | null;
  metricsProbe: { ok: boolean; status: number; bytes: number; sample?: string } | null;
  busy: boolean;
  currentSessionId: string | null;
  onRefresh: () => void;
  onLoadSessions: () => void;
  onRevokeSession: (id: string) => void;
  onProbeMetrics: () => void;
  onCopyBase: () => void;
}) {
  const deps = props.readiness?.dependencies ?? props.health?.dependencies ?? {};
  const depEntries = Object.entries(deps as Record<string, { status?: string; detail?: string }>);
  const mem = props.health?.memory;
  const detail = props.health?.detail === true;
  const sessions = props.adminSessions?.sessions ?? [];

  return (
    <div className="stack">
      <div className="section-head">
        <h3>System health</h3>
        <button type="button" onClick={props.onRefresh}>
          Refresh
        </button>
      </div>
      <p className="muted">
        Public probes via <code>/health/ready</code> and <code>/v2/health</code>. Detail fields
        unlock with metrics or admin token below. Auto-refresh every 15s.
      </p>

      <div className="card stack">
        <div className="row">
          <strong>Status</strong>
          <span
            className={
              props.healthOk === true ? "pill up" : props.healthOk === false ? "pill down" : "pill"
            }
          >
            {props.healthOk === true ? "UP" : props.healthOk === false ? "DOWN" : "…"}
          </span>
          <span className={detail ? "pill up" : "pill muted-pill"}>
            {detail ? "DETAIL" : "LEAN"}
          </span>
          {props.healthAt && <span className="muted">as of {props.healthAt}</span>}
        </div>
        {props.healthError && <div className="banner error">{props.healthError}</div>}
        <div className="row">
          <span className="subtle">
            Base URL: {props.baseUrl.trim() || "(same origin)"}
          </span>
          <button type="button" className="ghost compact" onClick={props.onCopyBase}>
            Copy URL
          </button>
        </div>
      </div>

      <div className="card stack">
        <strong>Ops tokens</strong>
        <p className="muted" style={{ margin: 0 }}>
          Stored in this browser only (<code>localStorage</code>). Used for health detail, admin
          sessions, and metrics probe — never sent unless you use those actions.
        </p>
        <label className="field">
          <span>X-Admin-Token</span>
          <input
            type="password"
            autoComplete="off"
            spellCheck={false}
            value={props.adminToken}
            onChange={(e) => props.setAdminToken(e.target.value)}
            placeholder="game.admin.token"
            aria-label="Admin token"
          />
        </label>
        <label className="field">
          <span>X-Metrics-Token</span>
          <input
            type="password"
            autoComplete="off"
            spellCheck={false}
            value={props.metricsToken}
            onChange={(e) => props.setMetricsToken(e.target.value)}
            placeholder="game.metrics.scrape-token"
            aria-label="Metrics scrape token"
          />
        </label>
        <div className="row">
          <button type="button" onClick={props.onRefresh} disabled={props.busy}>
            Refresh health (with tokens)
          </button>
          <button type="button" onClick={props.onProbeMetrics} disabled={props.busy}>
            Probe /metrics
          </button>
        </div>
        {props.metricsProbe ? (
          <div className="subtle">
            Metrics HTTP {props.metricsProbe.status} · {props.metricsProbe.bytes} B
            {props.metricsProbe.sample ? ` · ${props.metricsProbe.sample}` : ""}
          </div>
        ) : null}
      </div>

      <div className="card">
        <strong>Runtime</strong>
        <div className="stat-row">
          <span className="stat">
            Sessions <b>{props.health?.sessions ?? props.readiness?.sessions ?? "—"}</b>
          </span>
          <span className="stat">
            Engines <b>{props.health?.engines ?? props.readiness?.engines ?? "—"}</b>
          </span>
          <span className="stat">
            Uptime{" "}
            <b>
              {props.health?.uptimeSeconds != null
                ? formatUptime(props.health.uptimeSeconds)
                : "—"}
            </b>
          </span>
        </div>
        {mem && (
          <div className="mem-block">
            <div className="muted" style={{ marginTop: "0.5rem" }}>
              Heap free {fmtBytes(mem.freeBytes)} / total {fmtBytes(mem.totalBytes)} (max{" "}
              {fmtBytes(mem.maxBytes)})
            </div>
            {mem.maxBytes && mem.maxBytes > 0 ? (
              <div
                className="progress"
                role="progressbar"
                aria-valuenow={Math.round(((mem.totalBytes ?? 0) / mem.maxBytes) * 100)}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label="Heap usage vs max"
                style={{ marginTop: 8 }}
              >
                <span
                  style={{
                    width: `${Math.min(100, ((mem.totalBytes ?? 0) / mem.maxBytes) * 100)}%`,
                  }}
                />
              </div>
            ) : null}
          </div>
        )}
        {!detail && (
          <p className="muted">
            Lean view — add an ops token and Refresh to unlock sessions/engines/memory.
          </p>
        )}
      </div>

      <div className="card">
        <strong>Dependencies</strong>
        {depEntries.length === 0 ? (
          <p className="muted">
            {detail
              ? "No dependency data yet — hit Refresh."
              : "Hidden in lean health — unlock detail with ops tokens."}
          </p>
        ) : (
          depEntries.map(([name, check]) => (
            <div key={name} className="dep-row">
              <span>{name}</span>
              <span
                className={
                  check.status === "UP"
                    ? "pill up"
                    : check.status === "DOWN"
                      ? "pill down"
                      : "pill muted-pill"
                }
              >
                {check.status ?? "?"}
                {check.detail ? ` · ${check.detail}` : ""}
              </span>
            </div>
          ))
        )}
      </div>

      <div className="card stack">
        <div className="section-head">
          <strong>Admin sessions</strong>
          <button
            type="button"
            className="primary"
            disabled={props.busy || !props.adminToken.trim()}
            onClick={props.onLoadSessions}
          >
            Load sessions
          </button>
        </div>
        <p className="muted" style={{ margin: 0 }}>
          <code>GET /v2/admin/sessions</code> · revoke uses{" "}
          <code>DELETE /v2/admin/sessions/&#123;id&#125;</code>
        </p>
        {!props.adminToken.trim() ? (
          <div className="empty">Enter an admin token above to manage sessions.</div>
        ) : null}
        {props.adminSessions && sessions.length === 0 ? (
          <div className="empty">No sessions in inventory.</div>
        ) : null}
        {sessions.length > 0 ? (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Session</th>
                  <th>Name</th>
                  <th>Last seen</th>
                  <th>Engine</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {sessions.map((s: AdminSessionRow) => {
                  const id = s.sessionId ?? "";
                  const mine = props.currentSessionId && id === props.currentSessionId;
                  return (
                    <tr key={id} className={mine ? "is-mine" : undefined}>
                      <td>
                        <code title={id}>{id ? id.slice(0, 8) : "—"}</code>
                        {mine ? <span className="pill muted-pill">you</span> : null}
                      </td>
                      <td>{s.displayName ?? "—"}</td>
                      <td>{relativeEpoch(s.lastSeenEpochSeconds)}</td>
                      <td>
                        {s.hasEngine ? (
                          <span className="pill up">yes</span>
                        ) : (
                          <span className="pill muted-pill">no</span>
                        )}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="ghost compact danger-text"
                          disabled={props.busy || !id}
                          onClick={() => {
                            if (
                              window.confirm(
                                `Revoke session ${id.slice(0, 8)}…? Client must re-auth.`,
                              )
                            ) {
                              props.onRevokeSession(id);
                            }
                          }}
                        >
                          Revoke
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <div className="subtle">
              Showing {props.adminSessions?.count ?? sessions.length} of{" "}
              {props.adminSessions?.total ?? "?"}
            </div>
          </div>
        ) : null}
      </div>

      <div className="card">
        <strong>Audit logs</strong>
        <p className="muted" style={{ margin: 0 }}>
          Server emits <code>dm.admin.audit</code> and <code>dm.security.audit</code> lines (job ACL
          denials, rate limits, oversize bodies, bad ops tokens). Tail process logs in ops — not
          exposed in this SPA.
        </p>
      </div>
    </div>
  );
}

function formatUptime(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${r}s`;
  return `${r}s`;
}

function fmtBytes(n?: number): string {
  if (n == null || Number.isNaN(n)) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}
