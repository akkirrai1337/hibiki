import { describe, expect, it } from "vitest";
import { planSize, planSync, type SyncDirection } from "./librarySync";
import type { AnimeTitle, LibraryEntry, SourceLibraryEntry } from "@shared/types";

// Turning sync on is the one moment where both sides already hold things and disagree, and where
// the wrong rule silently deletes a library. These pin what each choice writes - and, for the
// destructive one, exactly what it removes - since the screen shows those counts before doing any
// of it and they have to be the same numbers the run then acts on.

function local(animeId: string, category: LibraryEntry["category"]): LibraryEntry {
  return { animeId, sourceId: "yummy-anime", category, addedAt: 0, anime: { id: animeId } as AnimeTitle };
}

function remote(animeId: string, category: SourceLibraryEntry["category"]): SourceLibraryEntry {
  return { animeId, category };
}

const fifty = Array.from({ length: 50 }, (_, i) => local(`local-${i}`, "watching"));
const three = [remote("r-1", "completed"), remote("r-2", "planned"), remote("r-3", "watching")];

describe("planSync", () => {
  it("writes nothing at all when the choice is to start from now on", () => {
    const plan = planSync("forward", fifty, three);
    expect(planSize(plan)).toBe(0);
  });

  it("pushes every local row the account does not already agree about", () => {
    const plan = planSync("push", fifty, three);
    expect(plan.push).toHaveLength(50);
    expect(plan.pull).toHaveLength(0);
    expect(plan.removeLocal).toHaveLength(0);
  });

  it("leaves rows both sides already agree about alone", () => {
    // Fifty identical rows re-sent is fifty requests that change nothing, and this API is rate
    // limited enough that it matters.
    const agreed = [local("a", "watching"), local("b", "completed")];
    const plan = planSync("push", agreed, [remote("a", "watching"), remote("b", "dropped")]);
    expect(plan.push.map((entry) => entry.animeId)).toEqual(["b"]);
  });

  it("pulling replaces: it takes the account's rows and drops local ones it does not have", () => {
    const plan = planSync("pull", [local("a", "watching"), local("gone", "planned")], [remote("a", "completed")]);
    expect(plan.pull.map((entry) => entry.animeId)).toEqual(["a"]);
    expect(plan.removeLocal.map((entry) => entry.animeId)).toEqual(["gone"]);
  });

  it("merging never deletes anything", () => {
    const plan = planSync("merge", [local("a", "watching"), local("mine", "planned")], [remote("a", "completed"), remote("theirs", "dropped")]);
    expect(plan.removeLocal).toHaveLength(0);
    expect(plan.pull.map((entry) => entry.animeId)).toEqual(["theirs"]);
    // "a" disagrees, and on a merge the local side wins because the button was pressed here.
    expect(plan.push.map((entry) => entry.animeId)).toEqual(["a", "mine"]);
  });

  it("merging pulls only titles this app does not have at all", () => {
    // A title both sides hold under different categories must not be pulled *and* pushed, or the
    // run would fight itself over one row.
    const plan = planSync("merge", [local("a", "watching")], [remote("a", "completed")]);
    expect(plan.pull).toHaveLength(0);
    expect(plan.push.map((entry) => entry.animeId)).toEqual(["a"]);
  });

  it("counts every write a plan will make", () => {
    const plan = planSync("pull", [local("a", "watching"), local("gone", "planned")], [remote("a", "completed"), remote("new", "dropped")]);
    expect(planSize(plan)).toBe(plan.push.length + plan.pull.length + plan.removeLocal.length);
    expect(planSize(plan)).toBe(3);
  });

  it("does nothing in any direction when both sides are empty", () => {
    for (const direction of ["push", "pull", "merge", "forward"] as SyncDirection[]) {
      expect(planSize(planSync(direction, [], []))).toBe(0);
    }
  });
});
