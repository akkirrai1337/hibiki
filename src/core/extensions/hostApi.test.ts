import { describe, expect, it, vi } from "vitest";

vi.mock("./netFetch", () => ({ performNetFetch: vi.fn(), performNetFetchAll: vi.fn() }));
vi.mock("../logger", () => ({ logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));
// Nothing may be written: the check has to come before the files are touched.
const writeText = vi.fn();
vi.mock("../platform", () => ({
  getPlatform: () => ({ files: { join: (...parts: string[]) => parts.join("/"), mkdir: vi.fn(), writeText } }),
}));

import { HOST_API } from "@shared/hostApi";
import { ExtensionRuntime, HostApiError } from "./runtime";

const manifest = (extra: Record<string, unknown>) => JSON.stringify({ id: "kodik", version: "1.0.5", ...extra });

describe("hostApi on install", () => {
  it("refuses a resolver that needs a newer app, before writing anything", async () => {
    const runtime = new ExtensionRuntime("ext");
    await expect(runtime.installResolver("kodik", manifest({ hostApi: HOST_API + 1 }), "var Provider = {};")).rejects.toBeInstanceOf(HostApiError);
    await expect(runtime.install("kodik", manifest({ hostApi: HOST_API + 1 }), "var Provider = {};", "https://repo")).rejects.toBeInstanceOf(HostApiError);
    expect(writeText).not.toHaveBeenCalled();
  });

  it("does not check a manifest that declares nothing", async () => {
    const runtime = new ExtensionRuntime("ext");
    (runtime as unknown as { reload: () => Promise<void> }).reload = async () => {};
    await runtime.installResolver("kodik", manifest({}), "var Provider = {};");
    expect(writeText).toHaveBeenCalled();
  });
});
