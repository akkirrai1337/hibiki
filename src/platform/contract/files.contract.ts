import { describe, expect, it } from "vitest";
import type { FilesPort } from "../types";

/** `dir` must be an empty scratch directory the port may write into. */
export function describeFilesContract(name: string, files: FilesPort, dir: () => string): void {
  describe(`FilesPort contract: ${name}`, () => {
    it("reports missing paths as absent rather than throwing", async () => {
      const missing = files.join(dir(), "nope");
      expect(await files.exists(missing)).toBe(false);
      expect(await files.stat(missing)).toBeNull();
      expect(await files.list(missing)).toEqual([]);
      await expect(files.remove(missing)).resolves.toBeUndefined();
    });

    it("round-trips text and bytes, creating nested directories", async () => {
      const nested = files.join(dir(), "a", "b");
      await files.mkdir(nested);
      await files.writeText(files.join(nested, "note.txt"), "привет");
      await files.writeBytes(files.join(nested, "data.bin"), new Uint8Array([0, 1, 255]));
      expect(await files.readText(files.join(nested, "note.txt"))).toBe("привет");
      expect([...(await files.readBytes(files.join(nested, "data.bin")))]).toEqual([0, 1, 255]);
      expect((await files.list(nested)).sort()).toEqual(["data.bin", "note.txt"]);
      const stat = await files.stat(files.join(nested, "data.bin"));
      expect(stat?.size).toBe(3);
      expect(stat?.isDirectory).toBe(false);
      expect((await files.stat(nested))?.isDirectory).toBe(true);
    });

    it("renames, and removes directories with their contents", async () => {
      const from = files.join(dir(), "from.txt");
      const to = files.join(dir(), "to.txt");
      await files.writeText(from, "x");
      await files.rename(from, to);
      expect(await files.exists(from)).toBe(false);
      expect(await files.readText(to)).toBe("x");

      const tree = files.join(dir(), "tree");
      await files.mkdir(files.join(tree, "inner"));
      await files.writeText(files.join(tree, "inner", "f.txt"), "y");
      await files.remove(tree);
      expect(await files.exists(tree)).toBe(false);
    });
  });
}
