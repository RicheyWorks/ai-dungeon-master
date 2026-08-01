/** Build a Steam-shaped receipt for POST /v2/entitlements/verify. */
export function steamReceipt(opts: {
  orderId: string;
  productId: string;
  steamId?: string;
}): string {
  const body: Record<string, string> = {
    orderId: opts.orderId.trim(),
    productId: opts.productId.trim(),
  };
  if (opts.steamId?.trim()) body.steamId = opts.steamId.trim();
  return JSON.stringify(body);
}

/** Optional bridge for Steamworks / Tauri shells. */
export type SteamDesktopBridge = {
  /** Return current SteamID64 if the overlay is available. */
  getSteamId?: () => string | null | Promise<string | null>;
  /** Start a MicroTxn (host-side) and return orderId. */
  initPurchase?: (productId: string) => Promise<string>;
};

declare global {
  interface Window {
    __dmSteam?: SteamDesktopBridge;
  }
}

export function steamBridge(): SteamDesktopBridge | undefined {
  return typeof window !== "undefined" ? window.__dmSteam : undefined;
}
