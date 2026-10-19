import { describe, expect, it, vi } from "vitest";
import { invokeProvider } from "./dispatch";

describe("invokeProvider getById", () => {
  it("passes the part on, so a script can answer with the title alone", () => {
    const getById = vi.fn((id: string, part?: string) => ({ id, part }));
    expect(invokeProvider({ getById }, "src", "getById", ["7", "core"])).toEqual({ id: "7", part: "core", sourceId: "src" });
    expect(getById).toHaveBeenCalledWith("7", "core");
  });

  it("asks for everything when no part is given - what an older host does", () => {
    const getById = vi.fn((id: string, part?: string) => ({ id, part }));
    expect(invokeProvider({ getById }, "src", "getById", ["7"])).toEqual({ id: "7", part: undefined, sourceId: "src" });
  });
});

describe("invokeProvider getTitleExtras", () => {
  it("returns what the script's getTitleExtras answers", () => {
    const getTitleExtras = vi.fn(() => ({ similarAnime: [{ id: "2", title: "Two" }] }));
    expect(invokeProvider({ getTitleExtras }, "src", "getTitleExtras", ["7"])).toEqual({ similarAnime: [{ id: "2", title: "Two" }] });
    expect(getTitleExtras).toHaveBeenCalledWith("7");
  });

  it("is null for a script without it - the host then has nothing to add", () => {
    expect(invokeProvider({}, "src", "getTitleExtras", ["7"])).toBeNull();
  });
});
