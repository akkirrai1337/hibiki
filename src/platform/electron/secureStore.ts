import { safeStorage } from "electron";
import type { SecureStorePort } from "../types";

/** DPAPI-backed on Windows, as main/extensions/extensionStorage.ts uses it. */
export const electronSecureStore: SecureStorePort = {
  isAvailable: async () => safeStorage.isEncryptionAvailable(),
  encrypt: async (plaintext) => safeStorage.encryptString(plaintext).toString("base64"),
  decrypt: async (ciphertext) => safeStorage.decryptString(Buffer.from(ciphertext, "base64")),
};
