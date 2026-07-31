/** HMAC-SHA256 mint matching server DevStorefront. */
export const DEV_STOREFRONT = "dev";
const DEFAULT_SECRET = "dev-storefront-insecure-secret-change-me";

function b64url(bytes: ArrayBuffer | Uint8Array): string {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (let i = 0; i < u8.length; i++) s += String.fromCharCode(u8[i]);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function signDevReceipt(
  productId: string,
  secret = DEFAULT_SECRET,
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
