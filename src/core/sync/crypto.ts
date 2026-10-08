// Device sync, the trust half: pairing two devices once, and everything after encrypted with the
// key they agreed on. WebCrypto only, which both Electron's main process and Android's WebView have.
//
// Pairing: the computer shows a six-digit code; the phone has the person type it. Both make an ECDH
// key pair, swap public keys, and derive the shared key from the ECDH secret with the code as salt.
// Each then proves it holds that key (an HMAC over a fixed label) - a wrong code, or anyone sitting
// between them with keys of their own, gives a different key and the proofs fail. The code itself
// never travels. (A six-digit code is not a PAKE: someone actively intercepting the pairing exchange,
// at that moment, on the same network, could guess it offline. For a one-minute window on a home
// network that is accepted; the sync traffic afterwards uses a full 256-bit key.)

/** Bytes over a plain ArrayBuffer - what WebCrypto takes. */
export type Bytes = Uint8Array<ArrayBuffer>;

const enc = new TextEncoder();
const dec = new TextDecoder();
const INFO = enc.encode("hibiki-device-sync v1");

export function toBase64(bytes: Uint8Array): string {
  let text = "";
  for (const byte of bytes) text += String.fromCharCode(byte);
  return btoa(text);
}

export function fromBase64(text: string): Bytes {
  const raw = atob(text);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

/** A pairing code: six digits, uniformly random. */
export function newPairingCode(): string {
  const value = crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
  return String(value).padStart(6, "0");
}

export interface PairingKeys {
  privateKey: CryptoKey;
  /** The public key to send, raw P-256 point in base64. */
  publicKey: string;
}

export async function newPairingKeys(): Promise<PairingKeys> {
  const pair = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, false, ["deriveBits"]);
  const raw = new Uint8Array(await crypto.subtle.exportKey("raw", pair.publicKey));
  return { privateKey: pair.privateKey, publicKey: toBase64(raw) };
}

/** The shared key both sides arrive at from their own private key, the other's public key and the code. */
export async function deriveSharedKey(own: CryptoKey, peerPublicKey: string, code: string): Promise<Bytes> {
  const peer = await crypto.subtle.importKey("raw", fromBase64(peerPublicKey), { name: "ECDH", namedCurve: "P-256" }, false, []);
  const secret = await crypto.subtle.deriveBits({ name: "ECDH", public: peer }, own, 256);
  const hkdfKey = await crypto.subtle.importKey("raw", secret, "HKDF", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "HKDF", hash: "SHA-256", salt: enc.encode(code), info: INFO }, hkdfKey, 256);
  return new Uint8Array(bits);
}

/** Shows the other side this one holds the key, without showing the key. */
export async function keyProof(key: Bytes, label: "server" | "client"): Promise<string> {
  const hmac = await crypto.subtle.importKey("raw", key, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return toBase64(new Uint8Array(await crypto.subtle.sign("HMAC", hmac, enc.encode(`hibiki-pair-${label}`))));
}

export async function checkProof(key: Bytes, label: "server" | "client", proof: string): Promise<boolean> {
  const expected = await keyProof(key, label);
  // Equal length by construction; compared in full either way.
  let diff = expected.length ^ proof.length;
  for (let i = 0; i < Math.min(expected.length, proof.length); i++) diff |= expected.charCodeAt(i) ^ proof.charCodeAt(i);
  return diff === 0;
}

export interface Sealed {
  iv: string;
  data: string;
}

export async function seal(key: Bytes, value: unknown): Promise<Sealed> {
  const aes = await crypto.subtle.importKey("raw", key, "AES-GCM", false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, aes, enc.encode(JSON.stringify(value))));
  return { iv: toBase64(iv), data: toBase64(data) };
}

/** Throws when the message was not sealed with this key, or was changed on the way. */
export async function open<T>(key: Bytes, sealed: Sealed): Promise<T> {
  const aes = await crypto.subtle.importKey("raw", key, "AES-GCM", false, ["decrypt"]);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: fromBase64(sealed.iv) }, aes, fromBase64(sealed.data));
  return JSON.parse(dec.decode(plain)) as T;
}
