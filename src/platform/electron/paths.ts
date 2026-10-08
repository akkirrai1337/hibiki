import path from "node:path";
import { app } from "electron";
import type { PlatformPaths } from "../types";

/** The layout under userData the app has always used - the same names main/ computes today. */
export function electronPaths(): PlatformPaths {
  const userData = app.getPath("userData");
  return {
    userData,
    database: path.join(userData, "hibiki.db"),
    extensions: path.join(userData, "extensions"),
    extensionStorage: path.join(userData, "extension-storage"),
    downloads: path.join(userData, "downloads"),
    profile: path.join(userData, "profile"),
  };
}
