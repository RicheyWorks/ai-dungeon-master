import {
  AdminApi,
  Configuration,
  HealthApi,
  ResponseError,
  V2Api,
  type ActionRequest,
  type CatalogPayload,
  type EntitlementPayload,
  type GameStatusV2,
  type HealthPayload,
  type LivenessResponse,
  type MarketplaceInstallJob as SdkMarketplaceInstallJob,
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

/** Correlation id for X-Request-Id (opaque, short). */
export function newRequestId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `web-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

function clientHeaders(token: string | null): Record<string, string> {
  const headers: Record<string, string> = {
    "X-Request-Id": newRequestId(),
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

/** Create a configured V2 client. */
export function createApi(baseUrl: string, token: string | null): V2Api {
  return new V2Api(
    new Configuration({
      basePath: resolveBase(baseUrl),
      headers: clientHeaders(token),
    }),
  );
}

function createAdminApi(baseUrl: string, adminToken: string): AdminApi {
  return new AdminApi(
    new Configuration({
      basePath: resolveBase(baseUrl),
      headers: {
        "X-Admin-Token": adminToken,
        "X-Request-Id": newRequestId(),
      },
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

/** Re-issue JWT for the current session (same session id). */
export async function refreshSession(baseUrl: string, token: string): Promise<SessionInfo> {
  const envelope = await createApi(baseUrl, token).refreshSessionV2();
  const p = envelope.payload;
  if (!p.token) throw new Error("Session token missing from refreshSessionV2");
  return {
    sessionId: p.sessionId,
    token: p.token,
    displayName: p.displayName ?? "Adventurer",
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
  try {
    await createApi(baseUrl, token).deleteSessionV2();
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    // Treat unauthorized as already logged out; rethrow other failures.
    if (!/401|Unauthorized|Authentication/i.test(msg)) {
      throw e instanceof Error ? e : new Error(msg);
    }
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

function asInstallJob(job: SdkMarketplaceInstallJob | null | undefined): MarketplaceInstallJob {
  if (!job?.jobId) throw new Error("install job missing jobId");
  return {
    jobId: job.jobId,
    packId: job.packId,
    phase: job.phase,
    bytesRead: job.bytesRead,
    bytesTotal: job.bytesTotal,
    percent: job.percent,
    message: job.message ?? undefined,
    cancelRequested: job.cancelRequested,
    error: job.error,
  };
}

async function sdkErrorMessage(e: unknown, fallback: string): Promise<string> {
  if (e instanceof ResponseError) {
    try {
      const env = (await e.response.json()) as { payload?: { message?: string }; requestId?: string };
      const msg = env.payload?.message;
      if (e.response.status === 429) {
        const retry = e.response.headers.get("Retry-After") || e.response.headers.get("X-RateLimit-Reset");
        const base = msg || "Rate limited";
        return retry ? `${base} — retry in ${retry}s` : base;
      }
      if (msg) {
        return env.requestId ? `${msg} (${env.requestId})` : msg;
      }
    } catch {
      /* ignore */
    }
    if (e.response.status === 429) {
      const retry = e.response.headers.get("Retry-After");
      return retry ? `Rate limited — retry in ${retry}s` : "Rate limited";
    }
    return `${fallback} ${e.response.status}`;
  }
  return e instanceof Error ? e.message : fallback;
}

/** List local marketplace packs (generated V2Api). */
export async function getMarketplace(
  baseUrl: string,
  token: string | null,
  query?: string,
): Promise<MarketplacePayload> {
  try {
    const env = await createApi(baseUrl, token).listMarketplaceV2({
      q: query?.trim() || undefined,
    });
    return (env.payload as MarketplacePayload) ?? { packs: [] };
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "marketplace"));
  }
}

/** Install a marketplace pack into the live catalog (sync). */
export async function installMarketplacePack(
  baseUrl: string,
  token: string | null,
  id: string,
): Promise<{ packId?: string; alreadyInstalled?: boolean; message?: string }> {
  try {
    const env = await createApi(baseUrl, token).installMarketplacePackV2({ id, async: false });
    const p = env.payload;
    return {
      packId: p?.packId,
      alreadyInstalled: p?.alreadyInstalled,
      message: p?.message,
    };
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "install"));
  }
}

/**
 * Start async install; returns job snapshot (HTTP 202).
 * Uses dedicated `install-async` OpenAPI op (typed job envelope).
 */
export async function startMarketplaceInstall(
  baseUrl: string,
  token: string | null,
  id: string,
): Promise<MarketplaceInstallJob> {
  try {
    const env = await createApi(baseUrl, token).installMarketplacePackAsyncV2({ id });
    return asInstallJob(env.payload);
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "install async"));
  }
}

export async function getMarketplaceInstallJob(
  baseUrl: string,
  token: string | null,
  jobId: string,
): Promise<MarketplaceInstallJob> {
  try {
    const env = await createApi(baseUrl, token).getMarketplaceInstallJobV2({ jobId });
    return asInstallJob(env.payload);
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "install job"));
  }
}

export async function cancelMarketplaceInstall(
  baseUrl: string,
  token: string | null,
  jobId: string,
): Promise<MarketplaceInstallJob | null> {
  try {
    const env = await createApi(baseUrl, token).cancelMarketplaceInstallJobV2({ jobId });
    return env.payload ? asInstallJob(env.payload) : null;
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "cancel job"));
  }
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
export async function fetchReadiness(
  baseUrl: string,
  opts?: { adminToken?: string; metricsToken?: string },
): Promise<{
  ok: boolean;
  body: ReadinessResponse | null;
  error?: string;
}> {
  const base = resolveBase(baseUrl);
  try {
    const headers: Record<string, string> = {
      Accept: "application/json",
      "X-Request-Id": newRequestId(),
    };
    if (opts?.metricsToken?.trim()) headers["X-Metrics-Token"] = opts.metricsToken.trim();
    if (opts?.adminToken?.trim()) headers["X-Admin-Token"] = opts.adminToken.trim();
    const res = await fetch(`${base}/health/ready`, { headers });
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
export async function fetchHealthV2(
  baseUrl: string,
  opts?: { adminToken?: string; metricsToken?: string },
): Promise<{
  ok: boolean;
  payload: HealthPayload | null;
  error?: string;
}> {
  const base = resolveBase(baseUrl);
  try {
    const headers: Record<string, string> = {
      Accept: "application/json",
      "X-Request-Id": newRequestId(),
    };
    if (opts?.metricsToken?.trim()) {
      headers["X-Metrics-Token"] = opts.metricsToken.trim();
    }
    if (opts?.adminToken?.trim()) {
      headers["X-Admin-Token"] = opts.adminToken.trim();
    }
    const res = await fetch(`${base}/v2/health`, { headers });
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

export type AdminSessionRow = {
  sessionId?: string;
  displayName?: string;
  createdAtEpochSeconds?: number;
  lastSeenEpochSeconds?: number;
  hasEngine?: boolean;
};

export type AdminSessionsPayload = {
  count?: number;
  total?: number;
  limit?: number;
  sessions?: AdminSessionRow[];
};

export async function listAdminSessions(
  baseUrl: string,
  adminToken: string,
  limit = 100,
): Promise<AdminSessionsPayload> {
  try {
    const env = await createAdminApi(baseUrl, adminToken).listAdminSessions({
      xAdminToken: adminToken,
      limit,
    });
    return (env.payload as AdminSessionsPayload) ?? { sessions: [] };
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "admin sessions"));
  }
}

export async function revokeAdminSession(
  baseUrl: string,
  adminToken: string,
  sessionId: string,
): Promise<{ sessionId?: string; revoked?: boolean; existed?: boolean }> {
  try {
    const env = await createAdminApi(baseUrl, adminToken).revokeAdminSession({
      xAdminToken: adminToken,
      sessionId,
    });
    return (env.payload as { sessionId?: string; revoked?: boolean; existed?: boolean }) ?? {};
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "admin revoke"));
  }
}

export type AdminReceiptRow = {
  fingerprint?: string;
  sessionId?: string;
  productId?: string;
  storefront?: string;
  redeemedAtEpochMs?: number;
};

export type AdminReceiptsPayload = {
  count?: number;
  limit?: number;
  receipts?: AdminReceiptRow[];
};

export async function listAdminReceipts(
  baseUrl: string,
  adminToken: string,
  limit = 25,
): Promise<AdminReceiptsPayload> {
  try {
    const env = await createAdminApi(baseUrl, adminToken).listAdminReceipts({
      xAdminToken: adminToken,
      limit,
    });
    return (env.payload as AdminReceiptsPayload) ?? { receipts: [] };
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "admin receipts"));
  }
}

export type AdminSessionPacksPayload = {
  sessionId?: string;
  enabledPackIds?: string[];
  overrides?: Record<string, unknown>;
  sessionScoped?: boolean;
};

export async function getAdminSessionPacks(
  baseUrl: string,
  adminToken: string,
  sessionId: string,
): Promise<AdminSessionPacksPayload> {
  try {
    const env = await createAdminApi(baseUrl, adminToken).getAdminSessionPacks({
      xAdminToken: adminToken,
      sessionId,
    });
    return (env.payload as AdminSessionPacksPayload) ?? {};
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "admin session packs"));
  }
}

export type AdminSessionsPurgedPayload = {
  idleTtlSeconds?: number;
  removedSessions?: number;
  removedEngines?: number;
  activeSessions?: number;
  activeEngines?: number;
};

export async function purgeIdleAdminSessions(
  baseUrl: string,
  adminToken: string,
  idleTtlSeconds = 86400,
  evictEngines = true,
): Promise<AdminSessionsPurgedPayload> {
  try {
    const env = await createAdminApi(baseUrl, adminToken).purgeIdleAdminSessions({
      xAdminToken: adminToken,
      idleTtlSeconds,
      evictEngines,
    });
    return (env.payload as AdminSessionsPurgedPayload) ?? {};
  } catch (e) {
    throw new Error(await sdkErrorMessage(e, "admin purge idle"));
  }
}

/** Probe metrics endpoint (token optional when scrape open). */
export async function probeMetrics(
  baseUrl: string,
  metricsToken?: string,
): Promise<{ ok: boolean; status: number; bytes: number; sample?: string }> {
  const base = resolveBase(baseUrl);
  const headers: Record<string, string> = {
    Accept: "text/plain",
    "X-Request-Id": newRequestId(),
  };
  if (metricsToken?.trim()) {
    headers["X-Metrics-Token"] = metricsToken.trim();
  }
  const res = await fetch(`${base}/metrics`, { headers });
  const text = await res.text();
  return {
    ok: res.ok,
    status: res.status,
    bytes: text.length,
    sample: text.slice(0, 120).replace(/\s+/g, " "),
  };
}

/** Catalog pack fields added for entitlement gates (extra JSON on PackInfo). */
export type CatalogPackExtras = {
  requiredProductIds?: string[];
  locked?: boolean;
};
