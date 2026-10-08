// Per-source persistent storage for extension scripts.
//
// Extensions had nowhere to keep anything between calls, which is fine for a catalog and useless
// for an account: a login is worth nothing if the token dies with the worker that fetched it. This
// gives each source its own small key/value store, owned by the host rather than by the script -
// the script asks for a value by name and never learns where it lives, which is what lets the
// value be encrypted here (platform.secureStore) without every extension having to care.
//
// Values are handed to a call as a plain snapshot and any writes come back with the result (see
// execute.ts), so scripts keep the synchronous, Rhino-shaped API they were written against and
// nothing needs a second bridge across the worker boundary.
//
// File access is asynchronous, so every operation on one source runs strictly after the previous
// one: two writes landing together must not both start from the same old values, and a read must
// see every write queued before it.
import { logger } from "../logger";
import { getPlatform } from "../platform";

/** Marks the encrypted form on disk, so a store written before encryption was available (or on a
 * machine where it never is) still reads back rather than being mistaken for ciphertext. */
const ENCRYPTED_PREFIX = "enc:v1:";

export class ExtensionStorage {
  private readonly cache = new Map<string, Record<string, string>>();
  private readonly queues = new Map<string, Promise<unknown>>();

  /** `storageDir` is resolved on first use, so constructing a store needs no platform yet. */
  constructor(private readonly storageDir: () => string) {}

  private fileFor(sourceId: string): string {
    const safeId = sourceId.replace(/[^a-zA-Z0-9._-]/g, "_");
    return getPlatform().files.join(this.storageDir(), `${safeId}.json`);
  }

  /** Runs `task` after every operation already queued for this source. */
  private enqueue<T>(sourceId: string, task: () => Promise<T>): Promise<T> {
    const previous = this.queues.get(sourceId) ?? Promise.resolve();
    const next = previous.then(task, task);
    const tail = next.then(
      () => undefined,
      () => undefined,
    );
    this.queues.set(sourceId, tail);
    void tail.then(() => {
      if (this.queues.get(sourceId) === tail) this.queues.delete(sourceId);
    });
    return next;
  }

  read(sourceId: string): Promise<Record<string, string>> {
    return this.enqueue(sourceId, async () => ({ ...(await this.load(sourceId)) }));
  }

  /** Applies the writes a call made. `null` removes a key; keys not mentioned are left alone. */
  apply(sourceId: string, writes: Record<string, string | null>): Promise<void> {
    if (Object.keys(writes).length === 0) return Promise.resolve();
    return this.enqueue(sourceId, async () => {
      const values = { ...(await this.load(sourceId)) };
      for (const [key, value] of Object.entries(writes)) {
        if (value === null) delete values[key];
        else values[key] = value;
      }
      this.cache.set(sourceId, values);
      await this.write(sourceId, values);
    });
  }

  clear(sourceId: string): Promise<void> {
    return this.enqueue(sourceId, async () => {
      this.cache.delete(sourceId);
      await getPlatform().files.remove(this.fileFor(sourceId));
    });
  }

  /** The cached values, reading the file on first use. Only ever called from inside the queue. */
  private async load(sourceId: string): Promise<Record<string, string>> {
    const cached = this.cache.get(sourceId);
    if (cached) return cached;

    const { files, secureStore } = getPlatform();
    const file = this.fileFor(sourceId);
    let values: Record<string, string> = {};
    if (await files.exists(file)) {
      try {
        const raw = await files.readText(file);
        const decoded = raw.startsWith(ENCRYPTED_PREFIX) ? await secureStore.decrypt(raw.slice(ENCRYPTED_PREFIX.length)) : raw;
        const parsed = JSON.parse(decoded) as unknown;
        if (parsed && typeof parsed === "object") values = parsed as Record<string, string>;
      } catch (error) {
        logger.warn("ext", `storage for '${sourceId}' unreadable, starting empty: ${String(error)}`);
      }
    }
    this.cache.set(sourceId, values);
    return values;
  }

  private async write(sourceId: string, values: Record<string, string>): Promise<void> {
    const { files, secureStore } = getPlatform();
    await files.mkdir(this.storageDir());
    const json = JSON.stringify(values);
    let payload = json;
    try {
      if (await secureStore.isAvailable()) {
        payload = ENCRYPTED_PREFIX + (await secureStore.encrypt(json));
      }
    } catch (error) {
      logger.warn("ext", `storage for '${sourceId}' saved unencrypted: ${String(error)}`);
    }
    await files.writeText(this.fileFor(sourceId), payload);
  }
}
