import { useCallback, useEffect, useRef, useState } from "react";
import type { CatalogPayload, EntitlementPayload, GameStatusV2 } from "./api";
import * as api from "./api";
import {
  DEV_STOREFRONT,
  KNOWN_STOREFRONTS,
  mintReceipt,
} from "./devReceipts";

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

type Tab = "game" | "mods" | "store";

export function App() {
  const [baseUrl, setBaseUrl] = useState(() => sessionStore.loadBaseUrl(DEFAULT_BASE));
  const [session, setSession] = useState<SessionInfo | null>(() => {
    const s = sessionStore.loadSession();
    return s && !isExpired(s) ? s : null;
  });
  const [status, setStatus] = useState<GameStatusV2 | null>(null);
  const [catalog, setCatalog] = useState<CatalogPayload | null>(null);
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
  const [storefront, setStorefront] = useState(DEV_STOREFRONT);
  const [receipt, setReceipt] = useState("");
  const [replace, setReplace] = useState(false);
  const stompRef = useRef<StompClient | null>(null);

  const token = session?.token ?? null;

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
      </header>

      {session && (
        <div className={`session-line${stompConnected ? " live" : ""}`}>
          Playing as {session.displayName} · {shortId(session.sessionId)}
          {stompConnected ? " · LIVE" : ""}
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
            if (!catalog) {
              void run(async (s) => setCatalog(await api.getCatalog(baseUrl, s.token)));
            }
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
          busy={busy}
          replace={replace}
          setReplace={setReplace}
          onReload={() =>
            void run(async (s) => setCatalog(await api.getCatalog(baseUrl, s.token)))
          }
          onToggle={(id, enable) =>
            void run(async (s) => setCatalog(await api.togglePack(baseUrl, s.token, id, enable)))
          }
          onUpload={(file) =>
            void run(async (s) => {
              setCatalog(await api.uploadPack(baseUrl, s.token, file, replace));
              setInfo(replace ? "Pack replaced" : "Pack uploaded");
            })
          }
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
          onRefresh={() =>
            void run(async (s) => setEntitlements(await api.listEntitlements(baseUrl, s.token)))
          }
          onVerify={() =>
            void run(async (s) => {
              try {
                const p = await api.verifyReceipt(baseUrl, s.token, {
                  productId,
                  receipt,
                  storefront: storefront || DEV_STOREFRONT,
                });
                setEntitlements(p);
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
              setInfo(
                p.granted
                  ? `Sandbox ${minted.storefront} granted: ${p.productId}`
                  : `Failed: ${p.reason}`,
              );
            })
          }
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
  busy: boolean;
  replace: boolean;
  setReplace: (v: boolean) => void;
  onReload: () => void;
  onToggle: (id: string, enable: boolean) => void;
  onUpload: (file: File) => void;
}) {
  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h3 style={{ margin: 0 }}>Content packs</h3>
        <button type="button" onClick={props.onReload} disabled={props.busy}>
          Reload
        </button>
      </div>

      <div className="card">
        <strong>Upload pack zip</strong>
        <p className="muted">POST /v2/catalog/packs — same endpoint as the web mod browser.</p>
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

      {!props.catalog && <div className="muted">Tap Reload to fetch the catalog.</div>}

      {(props.catalog?.contentPacks ?? []).map((pack) => (
        <div className="card switch" key={pack.id ?? pack.displayName}>
          <div>
            <div className="pack-title">{pack.displayName ?? pack.id ?? "?"}</div>
            <div className="muted">
              v{pack.version ?? "?"} · {pack.monsters ?? 0} monsters · {pack.items ?? 0} items
            </div>
          </div>
          <input
            type="checkbox"
            checked={pack.enabled === true}
            disabled={props.busy || !pack.id}
            onChange={(e) => pack.id && props.onToggle(pack.id, e.target.checked)}
          />
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
  onRefresh: () => void;
  onVerify: () => void;
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
          <p className="muted">None yet — sandbox-buy a SKU or verify a receipt.</p>
        ) : (
          owned.map((sku) => <div key={sku}>• {sku}</div>)
        )}
        {props.entitlements?.reason && (
          <div className="muted">Last: {props.entitlements.reason}</div>
        )}
      </div>

      <div className="card stack">
        <strong>Sandbox purchase</strong>
        <p className="muted">
          Mints a storefront-shaped receipt (HMAC sandbox; JSON envelopes for google_play /
          app_store) and posts it to POST /v2/entitlements/verify.
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
          onClick={props.onVerify}
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
