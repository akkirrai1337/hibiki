import { describe, expect, it } from "vitest";
import { createCallStorage } from "@shared/extensionCallStorage";

// The per-call store is what makes an account possible at all: a token has to outlive the worker
// that fetched it. The rules that matter are that a script reads what was there when the call
// started, and that only what it actually changed travels back - anything looser either loses a
// login or overwrites a value some other call wrote in the meantime.
describe("createCallStorage", () => {
  it("reads the snapshot it was given", () => {
    const { binding } = createCallStorage({ token: "abc" });
    expect(binding.get("token")).toBe("abc");
    expect(binding.get("missing")).toBeNull();
  });

  it("reports only the keys the call touched", () => {
    const { binding, writes } = createCallStorage({ token: "abc", locale: "ru" });
    binding.get("locale");
    binding.set("token", "def");
    // `locale` was read but never written, so it must not travel back - a call that only reads
    // should not be able to reassert a value another call has since changed.
    expect(writes).toEqual({ token: "def" });
  });

  it("reads back what it just wrote", () => {
    const { binding } = createCallStorage();
    binding.set("token", "abc");
    expect(binding.get("token")).toBe("abc");
  });

  it("records a removal as null rather than as an absence", () => {
    // The difference is the whole point: a missing key means "leave it alone", null means "delete
    // it". Signing out has to be able to say the second one.
    const { binding, writes } = createCallStorage({ token: "abc" });
    binding.remove("token");
    expect(binding.get("token")).toBeNull();
    expect(writes).toEqual({ token: null });
  });

  it("leaves the caller's snapshot untouched", () => {
    const snapshot = { token: "abc" };
    const { binding } = createCallStorage(snapshot);
    binding.set("token", "def");
    binding.set("extra", "1");
    expect(snapshot).toEqual({ token: "abc" });
  });

  it("stores non-string values as strings", () => {
    // Scripts are plain JS and hand over whatever they have; the store is a string map, and a
    // value that reads back as something other than what a script set is a bug nobody would look
    // for in the store.
    const { binding, writes } = createCallStorage();
    (binding.set as (key: string, value: unknown) => void)("expires", 1234);
    expect(binding.get("expires")).toBe("1234");
    expect(writes.expires).toBe("1234");
  });
});
