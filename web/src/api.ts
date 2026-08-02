import {
  Configuration,
  HealthApi,
  V2Api,
  type ActionRequest,
  type CatalogPayload,
  type EntitlementPayload,
  type GameStatusV2,
  type HealthPayload,
  type LivenessResponse,
  type NarrateRequest,
  type ReadinessResponse,
  type SessionRequest,
  type VerifyReceiptRequest,
} from "@ai-dungeon-master/client";
import type { SessionInfo } from "./sessionStore";

export type {
  CatalogPayload,
  EntitlementPayload,
  GameStatusV2,
  HealthPayload,
  LivenessResponse,
  ReadinessResponse,
};

/**
 * Resolve API base URL. Empty / whitespace means same origin (engine-hosted
 * SPA or Vite proxy) — never fall through to the SDK's localhost default.
 */
export function resolveBase(baseUrl: string): string {
  const trimmed = baseUrl.trim().replace(/\/$/, "");
  if (trimmed) return trimmed;
  if (typeof window !== "undefined" && window.location?.origin) {
    return window.location.origin;
  }
  return "http://127.0.0.1:8080";
}

/** Create a configured V2 client. */
export function createApi(baseUrl: string, token: string | null): V2Api {
  return new V2Api(
    new Configuration({
      basePath: resolveBase(baseUrl),
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    }),
  );
}

export async function mintSession(
  baseUrl: string,
  displayName?: string | null,
): Promise<SessionInfo> {
  const api = createApi(baseUrl, null);
  const req: SessionRequest | undefined = displayName?.trim()
    ? { displayName: displayName.trim() }
    : undefined;
  const envelope = await api.createSessionV2({ sessionRequest: req });
  const p = envelope.payload;
  if (!p.token) throw new Error("Session token missing from createSessionV2");
  return {
    sessionId: p.sessionId,
    token: p.token,
    displayName: p.displayName,
    expiresAtEpochSeconds: p.expiresAtEpochSeconds ?? 0,
    createdAtEpochSeconds: p.createdAtEpochSeconds ?? 0,
  };
}

export async function validateSession(baseUrl: string, token: string): Promise<boolean> {
  try {
    await createApi(baseUrl, token).getSessionMeV2();
    return true;
  } catch {
    return false;
  }
}

/** Explicit logout — server drops identity, pack prefs, and live engine. */
export async function logoutSession(baseUrl: string, token: string): Promise<void> {
  const root = (baseUrl || "").replace(/\/$/, "");
  const url = `${root}/v2/session`;
  const res = await fetch(url, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/json",
    },
  });
  if (!res.ok && res.status !== 401) {
    const text = await res.text().catch(() => "");
    throw new Error(`Logout failed (${res.status}): ${text || res.statusText}`);
  }
}

export async function getStatus(baseUrl: string, token: string): Promise<GameStatusV2> {
  const env = await createApi(baseUrl, token).getStatusV2();
  return env.payload;
}

export async function submitAction(
  baseUrl: string,
  token: string,
  choiceLabel: string,
): Promise<GameStatusV2> {
  const body: ActionRequest = { choiceLabel };
  const env = await createApi(baseUrl, token).submitActionV2({ actionRequest: body });
  return env.payload;
}

export async function narrateRest(
  baseUrl: string,
  token: string,
  prompt: string,
): Promise<string | undefined> {
  const body: NarrateRequest = { prompt };
  const env = await createApi(baseUrl, token).narrateV2({ narrateRequest: body });
  return env.payload.text ?? undefined;
}

export async function getCatalog(baseUrl: string, token: string): Promise<CatalogPayload> {
  return (await createApi(baseUrl, token).getCatalogV2()).payload;
}

export async function togglePack(
  baseUrl: string,
  token: string,
  id: string,
  enable: boolean,
): Promise<CatalogPayload> {
  const api = createApi(baseUrl, token);
  const env = enable
    ? await api.enablePackV2({ id })
    : await api.disablePackV2({ id });
  return env.payload;
}

export async function uploadPack(
  baseUrl: string,
  token: string,
  file: File,
  replace: boolean,
): Promise<CatalogPayload> {
  const env = await createApi(baseUrl, token).uploadPackV2({ file, replace });
  return env.payload;
}

export async function listEntitlements(
  baseUrl: string,
  token: string,
): Promise<EntitlementPayload> {
  return (await createApi(baseUrl, token).listEntitlementsV2()).payload;
}

export async function verifyReceipt(
  baseUrl: string,
  token: string,
  body: VerifyReceiptRequest,
): Promise<EntitlementPayload> {
  return (await createApi(baseUrl, token).verifyReceiptV2({ verifyReceiptRequest: body })).payload;
}

export async function saveGame(baseUrl: string, token: string) {
  return (await createApi(baseUrl, token).saveGameV2()).payload;
}

export async function loadGame(baseUrl: string, token: string): Promise<GameStatusV2> {
  return (await createApi(baseUrl, token).loadGameV2()).payload;
}

export async function resetGame(baseUrl: string, token: string): Promise<GameStatusV2> {
  return (await createApi(baseUrl, token).resetGameV2()).payload;
}

/** Marketplace listing row from GET /v2/marketplace. */
export type MarketplaceListing = {
  id: string;
  displayName?: string;
  version?: string;
  minEngineVersion?: string;
  description?: string;
  installed?: boolean;
  enabled?: boolean;
  requiredProductIds?: string[];
  locked?: boolean;
  sourcePath?: string;
  /** `local` or `remote` */
  source?: string;
  downloadUrl?: string;
  sha256?: string;
};

export type MarketplacePayload = {
  root?: string;
  remoteIndexUrl?: string | null;
  remoteOk?: boolean;
  remoteError?: string | null;
  available?: number;
  installed?: number;
  packs?: MarketplaceListing[];
};

/** List local marketplace packs (uses session token when auth is on). */
export async function getMarketplace(
  baseUrl: string,
  token: string | null,
  query?: string,
): Promise<MarketplacePayload> {
  const base = resolveBase(baseUrl);
  const qs = query?.trim() ? `?q=${encodeURIComponent(query.trim())}` : "";
  const res = await fetch(`${base}/v2/marketplace${qs}`, {
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!res.ok) throw new Error(`marketplace ${res.status}`);
  const env = (await res.json()) as { payload?: MarketplacePayload };
  return env.payload ?? { packs: [] };
}

/** Install a marketplace pack into the live catalog (sync). */
export async function installMarketplacePack(
  baseUrl: string,
  token: string | null,
  id: string,
): Promise<{ packId?: string; alreadyInstalled?: boolean; message?: string }> {
  const base = resolveBase(baseUrl);
  const res = await fetch(`${base}/v2/marketplace/${encodeURIComponent(id)}/install`, {
    method: "POST",
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!res.ok) {
    let msg = `install ${res.status}`;
    try {
      const env = (await res.json()) as { payload?: { message?: string } };
      if (env.payload?.message) msg = env.payload.message;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const env = (await res.json()) as {
    payload?: { packId?: string; alreadyInstalled?: boolean; message?: string };
  };
  return env.payload ?? {};
}

export type MarketplaceInstallJob = {
  jobId: string;
  packId?: string;
  phase?: string;
  bytesRead?: number;
  bytesTotal?: number;
  percent?: number;
  message?: string;
  cancelRequested?: boolean;
  error?: string | null;
};

/** Start async install; returns job snapshot (HTTP 202). */
export async function startMarketplaceInstall(
  baseUrl: string,
  token: string | null,
  id: string,
): Promise<MarketplaceInstallJob> {
  const base = resolveBase(baseUrl);
  const res = await fetch(
    `${base}/v2/marketplace/${encodeURIComponent(id)}/install?async=true`,
    {
      method: "POST",
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    },
  );
  if (!res.ok) {
    let msg = `install async ${res.status}`;
    try {
      const env = (await res.json()) as { payload?: { message?: string } };
      if (env.payload?.message) msg = env.payload.message;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }
  const env = (await res.json()) as { payload?: MarketplaceInstallJob };
  if (!env.payload?.jobId) throw new Error("install job missing jobId");
  return env.payload;
}

export async function getMarketplaceInstallJob(
  baseUrl: string,
  token: string | null,
  jobId: string,
): Promise<MarketplaceInstallJob> {
  const base = resolveBase(baseUrl);
  const res = await fetch(`${base}/v2/marketplace/jobs/${encodeURIComponent(jobId)}`, {
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!res.ok) throw new Error(`install job ${res.status}`);
  const env = (await res.json()) as { payload?: MarketplaceInstallJob };
  if (!env.payload) throw new Error("empty job payload");
  return env.payload;
}

export async function cancelMarketplaceInstall(
  baseUrl: string,
  token: string | null,
  jobId: string,
): Promise<MarketplaceInstallJob | null> {
  const base = resolveBase(baseUrl);
  const res = await fetch(`${base}/v2/marketplace/jobs/${encodeURIComponent(jobId)}`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });
  if (!res.ok) throw new Error(`cancel job ${res.status}`);
  const env = (await res.json()) as { payload?: MarketplaceInstallJob };
  return env.payload ?? null;
}

/** Poll async install until terminal phase. */
export async function pollMarketplaceInstall(
  baseUrl: string,
  token: string | null,
  jobId: string,
  onProgress?: (job: MarketplaceInstallJob) => void,
  intervalMs = 400,
  timeoutMs = 120_000,
): Promise<MarketplaceInstallJob> {
  const deadline = Date.now() + timeoutMs;
  let last: MarketplaceInstallJob | null = null;
  while (Date.now() < deadline) {
    last = await getMarketplaceInstallJob(baseUrl, token, jobId);
    onProgress?.(last);
    const phase = last.phase ?? "";
    if (phase === "DONE" || phase === "FAILED" || phase === "CANCELLED") {
      return last;
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  throw new Error(`install timed out${last ? ` (${last.phase})` : ""}`);
}

function createHealthApi(baseUrl: string): HealthApi {
  return new HealthApi(new Configuration({ basePath: resolveBase(baseUrl) }));
}

/** Liveness — always up when the process answers. No auth. */
export async function getLiveness(baseUrl: string): Promise<LivenessResponse> {
  return createHealthApi(baseUrl).getLiveness();
}

/**
 * Readiness with dependency map. Throws on network error; on 503 the SDK may
 * still throw — callers should use {@link fetchReadiness} for soft handling.
 */
export async function getReadiness(baseUrl: string): Promise<ReadinessResponse> {
  return createHealthApi(baseUrl).getReadiness();
}

/** Soft readiness: returns body for both 200 and 503. */
export async function fetchReadiness(baseUrl: string): Promise<{
  ok: boolean;
  body: ReadinessResponse | null;
  error?: string;
}> {
  const base = resolveBase(baseUrl);
  try {
    const res = await fetch(`${base}/health/ready`, {
      headers: { Accept: "application/json" },
    });
    const body = (await res.json()) as ReadinessResponse;
    return { ok: res.ok, body };
  } catch (e) {
    return {
      ok: false,
      body: null,
      error: e instanceof Error ? e.message : String(e),
    };
  }
}

/** Soft v2 health metrics envelope (works with 503 bodies). */
export async function fetchHealthV2(baseUrl: string): Promise<{
  ok: boolean;
  payload: HealthPayload | null;
  error?: string;
}> {
  const base = resolveBase(baseUrl);
  try {
    const res = await fetch(`${base}/v2/health`, {
      headers: { Accept: "application/json" },
    });
    const env = (await res.json()) as { payload?: HealthPayload };
    return { ok: res.ok, payload: env.payload ?? null };
  } catch (e) {
    return {
      ok: false,
      payload: null,
      error: e instanceof Error ? e.message : String(e),
    };
  }
}

/** Catalog pack fields added for entitlement gates (extra JSON on PackInfo). */
export type CatalogPackExtras = {
  requiredProductIds?: string[];
  locked?: boolean;
};
