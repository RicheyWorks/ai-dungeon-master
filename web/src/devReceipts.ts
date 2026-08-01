/** HMAC-SHA256 mint matching server storefront sandbox secrets. */

export const STOREFRONT_DEV = "dev";
export const STOREFRONT_GOOGLE_PLAY = "google_play";
export const STOREFRONT_APP_STORE = "app_store";
export const DEV_STOREFRONT = STOREFRONT_DEV;

export const KNOWN_STOREFRONTS = [
  STOREFRONT_DEV,
  STOREFRONT_GOOGLE_PLAY,
  STOREFRONT_APP_STORE,
] as const;

const SECRET_DEV = "dev-storefront-insecure-secret-change-me";
const SECRET_GOOGLE = "google-play-sandbox-insecure-secret";
const SECRET_APPLE = "app-store-sandbox-insecure-secret";
export const DEFAULT_PACKAGE_NAME = "com.xai.dungeonmaster";

function b64url(bytes: ArrayBuffer | Uint8Array): string {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (let i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function secretFor(storefront: string): string {
  switch (storefront.toLowerCase()) {
    case STOREFRONT_GOOGLE_PLAY:
      return SECRET_GOOGLE;
    case STOREFRONT_APP_STORE:
      return SECRET_APPLE;
    default:
      return SECRET_DEV;
  }
}

export async function signDevReceipt(
  productId: string,
  secret = SECRET_DEV,
): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const product = enc.encode(productId);
  const sig = await crypto.subtle.sign("HMAC", key, product);
  return `${b64url(product)}.${b64url(sig)}`;
}

export type MintedReceipt = {
  storefront: string;
  productId: string;
  receipt: string;
};

/** Mint a verify-ready receipt for the given storefront (JSON for Play / App Store). */
export async function mintReceipt(
  storefront: string,
  productId: string,
  packageName = DEFAULT_PACKAGE_NAME,
): Promise<MintedReceipt> {
  const id = (storefront || STOREFRONT_DEV).toLowerCase();
  const hmac = await signDevReceipt(productId, secretFor(id));
  let receipt = hmac;
  if (id === STOREFRONT_GOOGLE_PLAY) {
    receipt = JSON.stringify({
      packageName,
      productId,
      purchaseToken: hmac,
    });
  } else if (id === STOREFRONT_APP_STORE) {
    receipt = JSON.stringify({
      receiptData: hmac,
      productId,
    });
  }
  return { storefront: id, productId, receipt };
}
