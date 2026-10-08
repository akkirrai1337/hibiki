import { afterEach, describe, expect, it, vi } from "vitest";
import type { SearchRequest } from "@shared/types";
import type { HibikiApi } from "./hibiki";

function installApi(search: HibikiApi["sources"]["search"]) {
  const cancelSearch = vi.fn();
  vi.stubGlobal("window", {
    hibiki: { sources: { search, cancelSearch } } as unknown as HibikiApi,
  });
  return cancelSearch;
}

afterEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
});

describe("searchSource", () => {
  it("cancels the matching main-process request when its signal aborts", async () => {
    const search = vi.fn((_sourceId: string, _request: SearchRequest, _requestId?: string) => new Promise<never>(() => {}));
    const cancelSearch = installApi(search);
    const { searchSource } = await import("./hibiki");
    const controller = new AbortController();

    const result = searchSource("source", { query: "old" }, controller.signal);
    const requestId = search.mock.calls[0][2];
    controller.abort();

    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(requestId).toEqual(expect.any(String));
    expect(cancelSearch).toHaveBeenCalledOnce();
    expect(cancelSearch).toHaveBeenCalledWith(requestId);
  });

  it("does not cancel a request that has already completed", async () => {
    const search = vi.fn(async () => []);
    const cancelSearch = installApi(search);
    const { searchSource } = await import("./hibiki");
    const controller = new AbortController();

    await expect(searchSource("source", { query: "current" }, controller.signal)).resolves.toEqual([]);
    controller.abort();

    expect(cancelSearch).not.toHaveBeenCalled();
  });
});
