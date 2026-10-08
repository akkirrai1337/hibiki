import fs from "node:fs/promises";
import path from "node:path";
import type { FilesPort } from "../types";

function isMissing(error: unknown): boolean {
  return (error as NodeJS.ErrnoException)?.code === "ENOENT";
}

export const electronFiles: FilesPort = {
  join: (...parts) => path.join(...parts),
  async exists(target) {
    try {
      await fs.access(target);
      return true;
    } catch {
      return false;
    }
  },
  async stat(target) {
    try {
      const stat = await fs.stat(target);
      return { size: stat.size, mtimeMs: stat.mtimeMs, isDirectory: stat.isDirectory() };
    } catch (error) {
      if (isMissing(error)) return null;
      throw error;
    }
  },
  async list(dir) {
    try {
      return await fs.readdir(dir);
    } catch (error) {
      if (isMissing(error)) return [];
      throw error;
    }
  },
  readText: (target) => fs.readFile(target, "utf-8"),
  writeText: (target, text) => fs.writeFile(target, text, "utf-8"),
  async readBytes(target) {
    return new Uint8Array(await fs.readFile(target));
  },
  writeBytes: (target, data) => fs.writeFile(target, data),
  async mkdir(dir) {
    await fs.mkdir(dir, { recursive: true });
  },
  remove: (target) => fs.rm(target, { recursive: true, force: true }),
  rename: (from, to) => fs.rename(from, to),
};
