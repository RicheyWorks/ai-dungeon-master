import { useCallback, useEffect, useRef, useState } from "react";
import type {
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
  const stompRef = useRef<StompClient | null>(null);

  const token = session?.token ?? null;

  const pollHealth = useCallback(async (url = baseUrl) => {
    const ready = await api.fetchReadiness(url);
    const v2 = await api.fetchHealthV2(url);
    setReadiness(ready.body);
    setHealth(v2.payload);
    setHealthOk(ready.ok && v2.ok);
    setHealthError(ready.error ?? v2.error ?? null);
    setHealthAt(new Date().toLocaleTimeString());
  }, [baseUrl]);

  useEffect(() => {
    void pollHealth();
    const id = window.setInterval(() => void pollHealth(), 15_000);
    return () => window.clearInterval(id);
  }, [pollHealth]);

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

  return (
    <div className="app">
      <header className="bar">
        <input
          value={baseUrl}
          onChange={(e) => onBaseUrlChange(e.target.value)}
          placeholder="Server (empty = same origin / Vite proxy)"
          spellCheck={false}
        />
        <button type="button" onClick={refresh} disabled={busy}>
          {busy ? "…" : "Sync"}
        </button>
        <button type="button" onClick={startSession} disabled={busy}>
          {session ? "New session" : "Start session"}
        </button>
        {session ? (
          <button
            type="button"
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
                  // still clear local state if server already forgot us
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
      </header>

      {session && (
        <div className={`session-line${stompConnected ? " live" : ""}`}>
          Playing as {session.displayName} · {shortId(session.sessionId)}
          {stompConnected ? " · LIVE" : ""}
          {" · "}
          <span
            className={
              healthOk === true ? "pill up" : healthOk === false ? "pill down" : "pill"
            }
            title={healthError ?? readiness?.status ?? "checking"}
          >
            {healthOk === true ? "READY" : healthOk === false ? "NOT READY" : "…"}
          </span>
        </div>
      )}
      {!session && healthOk !== null && (
        <div className="session-line">
          Engine{" "}
          <span className={healthOk ? "pill up" : "pill down"}>
            {healthOk ? "READY" : "NOT READY"}
          </span>
          {healthAt ? ` · checked ${healthAt}` : ""}
        </div>
      )}
      {info && <div className="banner">{info}</div>}
      {error && <div className="banner error">Error: {error}</div>}

      <nav className="tabs">
        <button type="button" className={tab === "game" ? "active" : ""} onClick={() => setTab("game")}>
          Game
        </button>
        <button
          type="button"
          className={tab === "mods" ? "active" : ""}
          onClick={() => {
            setTab("mods");
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
          }}
        >
          Mods
        </button>
        <button
          type="button"
          className={tab === "store" ? "active" : ""}
          onClick={() => {
            setTab("store");
            if (!entitlements) {
              void run(async (s) => setEntitlements(await api.listEntitlements(baseUrl, s.token)));
            }
          }}
        >
          Store
        </button>
        <button
          type="button"
          className={tab === "system" ? "active" : ""}
          onClick={() => {
            setTab("system");
            void pollHealth();
          }}
        >
          System
        </button>
      </nav>

      {tab === "game" && (
        <GameTab
          status={status}
          busy={busy}
          stompConnected={stompConnected}
          narration={narration}
          streamBuffer={streamBuffer}
          prompt={prompt}
          setPrompt={setPrompt}
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
            setTab("store");
            if (!entitlements) {
              void run(async (s) => setEntitlements(await api.listEntitlements(baseUrl, s.token)));
            }
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
          onRefresh={() => void pollHealth()}
        />
      )}

      {/* silence unused token lint in edge cases */}
      {token ? null : null}
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
  onSave: () => void;
  onLoad: () => void;
  onReset: () => void;
}) {
  const { status } = props;
  const quest = status?.quest;
  const progress = Math.min(Math.max(quest?.progress ?? 0, 0), 1);
  const outcome =
    quest?.completed ? "Completed" : quest?.failed ? "Failed" : status?.combatActive ? "In combat!" : "In progress";

  return (
    <div className="stack">
      <div className="row">
        <button type="button" onClick={props.onSave} disabled={props.busy}>
          Save
        </button>
        <button type="button" onClick={props.onLoad} disabled={props.busy}>
          Load
        </button>
        <button type="button" onClick={props.onReset} disabled={props.busy}>
          Reset
        </button>
      </div>

      <div className="card">
        <h2>{quest?.title ?? "No active quest"}</h2>
        <div className="muted">
          {outcome} · Chaos {status?.chaosLevel ?? "?"}
        </div>
        <div className="progress">
          <span style={{ width: `${progress * 100}%` }} />
        </div>
        {status?.location && <div className="muted">Location: {status.location}</div>}
      </div>

      <section>
        <h3>Party</h3>
        {(status?.party ?? []).map((m, i) => {
          const hp = Math.max(m.hp ?? 0, 0);
          const maxHp = Math.max(m.maxHp ?? 1, 1);
          return (
            <div className="card" key={i}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <strong>{m.name ?? "?"}</strong>
                <span className="muted">
                  {m.role ?? ""} L{m.level ?? 1}
                </span>
              </div>
              <div className="progress">
                <span style={{ width: `${(hp / maxHp) * 100}%` }} />
              </div>
              <div className="muted">
                HP {hp}/{maxHp}
                {m.mana != null ? ` · MP ${m.mana}/${m.maxMana ?? m.mana}` : ""}
                {m.alive === false ? " · FALLEN" : ""}
                {(m.statuses ?? []).length ? ` · ${(m.statuses ?? []).join(", ")}` : ""}
              </div>
            </div>
          );
        })}
      </section>

      {(status?.recentEvents ?? []).length > 0 && (
        <section>
          <h3>The story so far</h3>
          <div className="card">
            {(status?.recentEvents ?? []).map((e, i) => (
              <div key={i} className="muted">
                {e}
              </div>
            ))}
          </div>
        </section>
      )}

      <section>
        <h3>Choices</h3>
        {(status?.availableChoices ?? []).length === 0 && (
          <div className="muted">No choices available.</div>
        )}
        {(status?.availableChoices ?? []).map((label) => (
          <button
            key={label}
            type="button"
            className="choice primary"
            disabled={props.busy}
            onClick={() => props.onAct(label)}
          >
            {label}
          </button>
        ))}
      </section>

      <section>
        <h3>
          {props.stompConnected ? "Ask the Dungeon Master (live stream)" : "Ask the Dungeon Master"}
        </h3>
        <textarea
          rows={3}
          value={props.prompt}
          onChange={(e) => props.setPrompt(e.target.value)}
          placeholder="What do you do?"
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
        </div>
        {props.streamBuffer && (
          <div className="card stream">{props.streamBuffer}</div>
        )}
        {props.narration && <div className="card narration">{props.narration}</div>}
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

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3 style={{ margin: 0 }}>Marketplace</h3>
        <button type="button" onClick={props.onReload} disabled={props.busy || jobActive}>
          Reload
        </button>
      </div>
      {job && (
        <div className="card stack">
          <div className="row" style={{ justifyContent: "space-between" }}>
            <strong>
              Install {job.packId ?? "…"} · {job.phase ?? "…"}
            </strong>
            {jobActive && (
              <button type="button" onClick={props.onCancelInstall}>
                Cancel
              </button>
            )}
          </div>
          <div
            style={{
              height: 10,
              borderRadius: 6,
              background: "rgba(255,255,255,0.08)",
              overflow: "hidden",
            }}
          >
            <div
              style={{
                height: "100%",
                width: `${Math.min(100, Math.max(0, job.percent ?? 0))}%`,
                background: "linear-gradient(90deg, #6ee7b7, #34d399)",
                transition: "width 0.2s ease",
              }}
            />
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
        Local discovery from <code>/v2/marketplace</code>
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

      <div className="card row">
        <input
          value={props.marketQuery}
          onChange={(e) => props.setMarketQuery(e.target.value)}
          placeholder="Search packs…"
          onKeyDown={(e) => {
            if (e.key === "Enter") props.onSearch();
          }}
        />
        <button type="button" onClick={props.onSearch} disabled={props.busy}>
          Search
        </button>
      </div>

      {marketPacks.length === 0 && props.marketplace && (
        <div className="muted">No marketplace packs match.</div>
      )}

      {marketPacks.map((pack) => (
        <div className="card stack" key={pack.id}>
          <div className="row" style={{ justifyContent: "space-between" }}>
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
                <div className="muted" style={{ wordBreak: "break-all" }}>
                  {pack.downloadUrl}
                </div>
              )}
              {pack.sha256 && (
                <div className="muted" title={pack.sha256}>
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

      <div className="row" style={{ justifyContent: "space-between", marginTop: "0.5rem" }}>
        <h3 style={{ margin: 0 }}>Live catalog</h3>
      </div>

      <div className="card">
        <strong>Upload pack zip</strong>
        <p className="muted">POST /v2/catalog/packs</p>
        <label className="switch">
          <span>Replace if exists</span>
          <input
            type="checkbox"
            checked={props.replace}
            onChange={(e) => props.setReplace(e.target.checked)}
            disabled={props.busy}
          />
        </label>
        <input
          type="file"
          accept=".zip,application/zip"
          disabled={props.busy}
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) props.onUpload(f);
            e.target.value = "";
          }}
        />
      </div>

      {livePacks.length === 0 && (
        <div className="muted">No live packs yet — install from the marketplace or upload a zip.</div>
      )}

      {livePacks.map((pack) => (
        <div className="card switch" key={pack.id ?? pack.displayName}>
          <div>
            <div className="pack-title">
              {pack.displayName ?? pack.id ?? "?"}
              {pack.locked ? (
                <span className="pill muted-pill" title={(pack.requiredProductIds ?? []).join(", ")}>
                  {" "}LOCKED
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
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3 style={{ margin: 0 }}>Entitlements</h3>
        <button type="button" onClick={props.onRefresh} disabled={props.busy}>
          Refresh
        </button>
      </div>

      <div className="card">
        <strong>Owned products</strong>
        {owned.length === 0 ? (
          <p className="muted">None yet — buy via Steam/desktop, sandbox, or verify a receipt.</p>
        ) : (
          owned.map((sku) => <div key={sku}>• {sku}</div>)
        )}
        {props.entitlements?.reason && (
          <div className="muted">Last: {props.entitlements.reason}</div>
        )}
      </div>

      {props.unlockHint ? (
        <div className="card" style={{ borderColor: "var(--accent, #7c6af7)" }}>
          <strong>Unlock pack</strong>
          <p className="muted" style={{ marginBottom: 8 }}>{props.unlockHint}</p>
          <div className="row">
            <button
              type="button"
              className="primary"
              disabled={props.busy || !props.productId.trim()}
              onClick={() => {
                props.onSandboxBuy(props.productId.trim(), props.storefront || DEV_STOREFRONT);
              }}
            >
              Buy {props.productId} (sandbox) now
            </button>
            <button type="button" disabled={props.busy} onClick={props.onClearUnlockHint}>
              Dismiss
            </button>
          </div>
        </div>
      ) : null}

      <div className="card stack">
        <strong>Steam desktop (orderId)</strong>
        <p className="muted">
          Steamworks MicroTxn: paste orderId from InitTxn, then verify storefront{" "}
          <code>steam</code>. Live engines QueryTxn then FinalizeTxn after grant (
          <code>desktop/STEAM.md</code>). Optional bridge: <code>window.__dmSteam</code>.
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
        <p className="muted">
          Mints a storefront-shaped receipt (HMAC sandbox; JSON envelopes for google_play /
          app_store / steam) and posts it to POST /v2/entitlements/verify.
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
        <input value={props.productId} onChange={(e) => props.setProductId(e.target.value)} />
        <button
          type="button"
          className="primary"
          disabled={props.busy || !props.productId.trim()}
          onClick={() => props.onSandboxBuy(props.productId.trim(), props.storefront)}
        >
          Buy with {props.storefront} sandbox receipt
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

      <div>
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
      </div>
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
  onRefresh: () => void;
}) {
  const deps = props.readiness?.dependencies ?? props.health?.dependencies ?? {};
  const depEntries = Object.entries(deps as Record<string, { status?: string; detail?: string }>);
  const mem = props.health?.memory;

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3 style={{ margin: 0 }}>System health</h3>
        <button type="button" onClick={props.onRefresh}>
          Refresh
        </button>
      </div>
      <p className="muted">
        Public probes via <code>/health/ready</code> and <code>/v2/health</code> (no session
        required). Auto-refreshes every 15s.
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
          {props.healthAt && <span className="muted">as of {props.healthAt}</span>}
        </div>
        {props.healthError && <div className="banner error">{props.healthError}</div>}
        <div className="muted">
          Base URL: {props.baseUrl.trim() || "(same origin)"}
        </div>
      </div>

      <div className="card">
        <strong>Metrics</strong>
        <div className="row" style={{ marginTop: "0.5rem" }}>
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
          <div className="muted" style={{ marginTop: "0.5rem" }}>
            Heap free {fmtBytes(mem.freeBytes)} / total {fmtBytes(mem.totalBytes)} (max{" "}
            {fmtBytes(mem.maxBytes)})
          </div>
        )}
      </div>

      <div className="card stack">
        <strong>Dependencies</strong>
        {depEntries.length === 0 ? (
          <p className="muted">No dependency data yet — hit Refresh.</p>
        ) : (
          depEntries.map(([name, check]) => (
            <div key={name} className="switch">
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
