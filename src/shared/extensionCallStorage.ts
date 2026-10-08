/**
 * The script-facing store for one extension call, plus the writes it made.
 *
 * Reads come from a snapshot handed in when the call is dispatched; writes are collected and
 * applied by whoever owns the real store once the call returns. That keeps the binding
 * synchronous - extension scripts have no way to await anything - without a bridge back to the
 * main thread for every key touched.
 *
 * Lives in shared rather than beside the runtime because it is plain logic with nothing of the
 * host in it, which is also what makes it testable.
 */
export interface ExtensionStorageBinding {
  get(key: string): string | null;
  set(key: string, value: string): void;
  remove(key: string): void;
}

export function createCallStorage(snapshot: Record<string, string> = {}): {
  binding: ExtensionStorageBinding;
  writes: Record<string, string | null>;
} {
  const values = { ...snapshot };
  // Only what the call actually changed: a key merely read must not travel back, or a call that
  // read a token would reassert it over one a later call had already replaced.
  const writes: Record<string, string | null> = {};
  return {
    binding: {
      get: (key) => (key in values ? values[key] : null),
      set: (key, value) => {
        values[key] = String(value);
        writes[key] = String(value);
      },
      // Null rather than absent: a missing key means "leave it alone", and signing out has to be
      // able to say "delete it".
      remove: (key) => {
        delete values[key];
        writes[key] = null;
      },
    },
    writes,
  };
}
