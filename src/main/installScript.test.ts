// scripts/install.sh, run by a real sh against a local "release" whose manifest is written by the very
// script CI uses (scripts/make-update-manifest.mjs) - so the format and its reader are tested together.
// `ditto` and `xattr`, which only macOS has, are stood in for.
import { execFile, execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

const run = promisify(execFile);
const root = path.resolve(__dirname, "../..");
const fwd = (value: string) => value.replace(/\\/g, "/");
const fileUrl = (value: string) => `file://${fwd(value).startsWith("/") ? "" : "/"}${fwd(value)}`;

const have = (command: string) => {
  try {
    execFileSync("sh", ["-c", `command -v ${command}`], { stdio: "ignore" });
    return true;
  } catch {
    return false;
  }
};

describe.skipIf(!have("curl") || !have("awk"))("install.sh", () => {
  let dir: string;
  let release: string;
  let prefix: string;
  let bin: string;
  let log: string;

  const bytes = (name: string) => `contents of ${name}`;

  beforeEach(async () => {
    dir = fwd(fs.mkdtempSync(path.join(os.tmpdir(), "hibiki-install-")));
    release = `${dir}/release`;
    prefix = `${dir}/prefix`;
    bin = `${dir}/bin`;
    log = `${dir}/log.txt`;
    for (const folder of [release, prefix, bin]) fs.mkdirSync(folder);
    for (const name of ["hibiki-v9.9.9-setup.exe", "hibiki-v9.9.9-mac-arm64.zip", "hibiki-v9.9.9-mac-x64.zip", "hibiki-v9.9.9-linux-x64.AppImage", "hibiki-v9.9.9.apk"]) {
      fs.writeFileSync(`${release}/${name}`, bytes(name));
    }
    await run("node", [path.join(root, "scripts/make-update-manifest.mjs"), "9.9.9", release, `${release}/update-manifest.json`]);
    // macOS's own tools: ditto unpacks to an app folder, xattr only notes that it was called.
    fs.writeFileSync(`${bin}/ditto`, `#!/bin/sh\nmkdir -p "$3/hibiki.app/Contents/MacOS"\ncp "$2" "$3/hibiki.app/Contents/MacOS/hibiki"\necho "ditto $1" >> "${log}"\n`, { mode: 0o755 });
    fs.writeFileSync(`${bin}/xattr`, `#!/bin/sh\necho "xattr $1 $2" >> "${log}"\n`, { mode: 0o755 });
  });
  afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

  const install = (env: Record<string, string>) =>
    run("sh", [path.join(root, "scripts/install.sh")], {
      env: {
        ...process.env,
        PATH: `${bin}${path.delimiter}${process.env.PATH}`,
        HIBIKI_MANIFEST: fileUrl(`${release}/update-manifest.json`),
        HIBIKI_RELEASE_BASE: fileUrl(release),
        HIBIKI_PREFIX: prefix,
        HIBIKI_APPLICATIONS: `${dir}/applications`,
        HOME: dir,
        ...env,
      },
    });
  const failure = async (env: Record<string, string>) => {
    try {
      await install(env);
    } catch (error) {
      return error as { code: number; stderr: string };
    }
    throw new Error("expected the install to fail");
  };

  it("installs the AppImage on Linux, with a menu entry", async () => {
    const { stdout } = await install({ HIBIKI_OS: "linux", HIBIKI_ARCH: "x86_64" });
    expect(stdout).toContain("hibiki 9.9.9 for linux-x64");
    expect(fs.readFileSync(`${prefix}/hibiki`, "utf8")).toBe(bytes("hibiki-v9.9.9-linux-x64.AppImage"));
    if (process.platform !== "win32") expect(fs.statSync(`${prefix}/hibiki`).mode & 0o111).not.toBe(0);
    const entry = fs.readFileSync(`${dir}/applications/hibiki.desktop`, "utf8");
    expect(entry).toContain(`Exec=${prefix}/hibiki %U`);
    expect(entry).toContain("x-scheme-handler/hibiki");
  });

  it("installs the app on macOS for either processor, replacing an old copy, and clears quarantine", async () => {
    fs.mkdirSync(`${prefix}/hibiki.app/Contents/MacOS`, { recursive: true });
    fs.writeFileSync(`${prefix}/hibiki.app/Contents/MacOS/hibiki`, "old");
    await install({ HIBIKI_OS: "Darwin", HIBIKI_ARCH: "arm64" });
    expect(fs.readFileSync(`${prefix}/hibiki.app/Contents/MacOS/hibiki`, "utf8")).toBe(bytes("hibiki-v9.9.9-mac-arm64.zip"));
    expect(fs.readFileSync(log, "utf8")).toBe(`ditto -xk\nxattr -cr ${prefix}/hibiki.app\n`);

    await install({ HIBIKI_OS: "Darwin", HIBIKI_ARCH: "x86_64" });
    expect(fs.readFileSync(`${prefix}/hibiki.app/Contents/MacOS/hibiki`, "utf8")).toBe(bytes("hibiki-v9.9.9-mac-x64.zip"));
  });

  it("says so, and installs nothing, when the release has no build for this system", async () => {
    const result = await failure({ HIBIKI_OS: "linux", HIBIKI_ARCH: "aarch64" });
    expect(result.code).toBe(1);
    expect(result.stderr).toContain("has no build for linux-arm64");
    expect(fs.existsSync(`${prefix}/hibiki`)).toBe(false);
  });

  it("refuses a file that is not the one the release lists, and installs nothing", async () => {
    fs.writeFileSync(`${release}/hibiki-v9.9.9-linux-x64.AppImage`, "tampered with");
    const result = await failure({ HIBIKI_OS: "linux", HIBIKI_ARCH: "x86_64" });
    expect(result.stderr).toContain("does not match the checksum");
    expect(fs.existsSync(`${prefix}/hibiki`)).toBe(false);
  });

  it("is for macOS and Linux only", async () => {
    const result = await failure({ HIBIKI_OS: "MINGW64_NT", HIBIKI_ARCH: "x86_64" });
    expect(result.stderr).toContain("macOS and Linux");
  });

  it("reports an unreachable release instead of carrying on", async () => {
    const result = await failure({ HIBIKI_OS: "linux", HIBIKI_ARCH: "x86_64", HIBIKI_MANIFEST: fileUrl(`${dir}/nowhere.json`) });
    expect(result.stderr).toContain("could not read the release manifest");
  });
});
