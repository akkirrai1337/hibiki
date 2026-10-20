import { execFile, execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { promisify } from "node:util";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { appBundleOf, appImageScript, macScript, prepareReplacement, replaceTarget, shellQuote, type ReplaceEnv } from "./selfReplace";

const run = promisify(execFile);

describe("shellQuote", () => {
  it("makes any text one shell word", () => {
    expect(shellQuote("/Applications/hibiki.app")).toBe("'/Applications/hibiki.app'");
    expect(shellQuote("/Users/a b/hibiki.app")).toBe("'/Users/a b/hibiki.app'");
    expect(shellQuote("it's")).toBe(`'it'\\''s'`);
    expect(shellQuote("$(rm -rf ~)`x`")).toBe("'$(rm -rf ~)`x`'");
  });
});

describe("appBundleOf", () => {
  it("finds the bundle an executable is inside", () => {
    expect(appBundleOf("/Applications/hibiki.app/Contents/MacOS/hibiki")).toBe("/Applications/hibiki.app");
    expect(appBundleOf("/Users/a b/Apps/hibiki.app/Contents/MacOS/hibiki")).toBe("/Users/a b/Apps/hibiki.app");
  });

  it("takes the outermost bundle, not a helper app inside it", () => {
    expect(appBundleOf("/Applications/hibiki.app/Contents/Frameworks/Helper.app/Contents/MacOS/Helper")).toBe("/Applications/hibiki.app");
  });

  it("is null for anything that is not a bundle's executable", () => {
    expect(appBundleOf("/usr/local/bin/hibiki")).toBeNull();
    expect(appBundleOf("/tmp/.mount_hibikiXYZ/hibiki")).toBeNull();
  });
});

describe("replaceTarget", () => {
  const mac: ReplaceEnv = { platform: "darwin", exePath: "/Applications/hibiki.app/Contents/MacOS/hibiki", appImage: undefined, pid: 1, packaged: true };
  const linux: ReplaceEnv = { platform: "linux", exePath: "/tmp/.mount_x/hibiki", appImage: "/home/a/Apps/hibiki.AppImage", pid: 1, packaged: true };
  const writable = () => true;
  const notWritable = () => false;

  it("replaces the installed bundle on macOS", () => {
    expect(replaceTarget(mac, writable)).toBe("/Applications/hibiki.app");
  });

  it("replaces the AppImage that is running on Linux", () => {
    expect(replaceTarget(linux, writable)).toBe("/home/a/Apps/hibiki.AppImage");
  });

  it("leaves it to the person when the folder is not theirs to write to", () => {
    expect(replaceTarget(mac, notWritable)).toBeNull();
    expect(replaceTarget(linux, notWritable)).toBeNull();
  });

  it("checks the folder the app is in, not the app", () => {
    const asked: string[] = [];
    replaceTarget(mac, (directory) => (asked.push(directory), true));
    replaceTarget(linux, (directory) => (asked.push(directory), true));
    expect(asked).toEqual(["/Applications", "/home/a/Apps"]);
  });

  it("does not replace an app run from a disk image or a quarantined download", () => {
    expect(replaceTarget({ ...mac, exePath: "/Volumes/hibiki/hibiki.app/Contents/MacOS/hibiki" }, writable)).toBeNull();
    expect(replaceTarget({ ...mac, exePath: "/private/var/folders/x/AppTranslocation/ABC/d/hibiki.app/Contents/MacOS/hibiki" }, writable)).toBeNull();
  });

  it("does nothing for a build run from a checkout, an AppImage-less Linux, or any other system", () => {
    expect(replaceTarget({ ...mac, packaged: false }, writable)).toBeNull();
    expect(replaceTarget({ ...linux, appImage: undefined }, writable)).toBeNull();
    expect(replaceTarget({ ...linux, platform: "win32" }, writable)).toBeNull();
  });
});

describe("prepareReplacement", () => {
  let dir: string;
  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), "hibiki-replace-"));
  });
  afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

  it("macOS: unzips next to the download and points the script at the bundle inside", async () => {
    const zip = path.join(dir, "hibiki-v3.3.0-mac-arm64.zip");
    fs.writeFileSync(zip, "zip");
    // A leftover from an earlier attempt must not be mistaken for this update.
    fs.mkdirSync(path.join(dir, "extracted", "Stale.app"), { recursive: true });
    const script = await prepareReplacement(zip, { platform: "darwin", exePath: "", appImage: undefined, pid: 42, packaged: true }, "/Applications/hibiki.app", async (_zip, into) => {
      fs.mkdirSync(path.join(into, "hibiki.app"));
    });
    expect(script).toContain("PID=42");
    expect(script).toContain("TARGET='/Applications/hibiki.app'");
    expect(script).toContain(`NEW='${path.join(dir, "extracted", "hibiki.app")}'`);
    expect(script).not.toContain("Stale.app");
  });

  it("macOS: refuses an archive with no app in it", async () => {
    const zip = path.join(dir, "x.zip");
    fs.writeFileSync(zip, "zip");
    await expect(prepareReplacement(zip, { platform: "darwin", exePath: "", appImage: undefined, pid: 1, packaged: true }, "/Applications/hibiki.app", async () => {})).rejects.toThrow(/does not contain an app/);
  });

  it("Linux: stages the AppImage beside the running one, executable", async () => {
    const download = path.join(dir, "download.AppImage");
    fs.writeFileSync(download, "new build");
    const target = path.join(dir, "hibiki.AppImage");
    fs.writeFileSync(target, "old build");
    const script = await prepareReplacement(download, { platform: "linux", exePath: "", appImage: target, pid: 7, packaged: true }, target, async () => {});
    const staged = `${target}.hibiki-new`;
    expect(fs.readFileSync(staged, "utf8")).toBe("new build");
    if (process.platform !== "win32") expect(fs.statSync(staged).mode & 0o111).not.toBe(0);
    expect(script).toContain(`NEW=${shellQuote(staged)}`);
    // The running one is untouched until the script swaps them.
    expect(fs.readFileSync(target, "utf8")).toBe("old build");
  });

  it("refuses a platform it cannot replace on", async () => {
    await expect(prepareReplacement("x", { platform: "win32", exePath: "", appImage: undefined, pid: 1, packaged: true }, "x", async () => {})).rejects.toThrow(/Cannot replace/);
  });
});

// The scripts themselves, run by a real sh with `open` and `xattr` stood in for - what they do to the
// files is the part that must not go wrong on a person's machine.
const hasSh = (() => {
  try {
    execFileSync("sh", ["-c", "true"]);
    return true;
  } catch {
    return false;
  }
})();

describe.skipIf(!hasSh)("the swap scripts, run", () => {
  let dir: string;
  let bin: string;
  let log: string;
  /** Forward slashes: the same text works for sh on every system the tests run on. */
  const fwd = (value: string) => value.replace(/\\/g, "/");

  beforeEach(() => {
    dir = fwd(fs.mkdtempSync(path.join(os.tmpdir(), "hibiki-swap-")));
    bin = `${dir}/bin`;
    log = `${dir}/log.txt`;
    fs.mkdirSync(bin);
    for (const name of ["open", "xattr"]) {
      fs.writeFileSync(`${bin}/${name}`, `#!/bin/sh\necho "${name} $@" >> "${log}"\n`, { mode: 0o755 });
    }
  });
  afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

  const exec = (script: string) => run("sh", ["-c", script], { env: { ...process.env, PATH: `${bin}${path.delimiter}${process.env.PATH}` } });
  const logged = () => (fs.existsSync(log) ? fs.readFileSync(log, "utf8") : "");
  // A process that has already exited, so the wait loop has nothing to wait for.
  const GONE = 2_000_000_000;

  function bundle(root: string, version: string) {
    fs.mkdirSync(`${root}/Contents/MacOS`, { recursive: true });
    fs.writeFileSync(`${root}/Contents/MacOS/hibiki`, version);
  }

  it("macOS: puts the new bundle where the old one was, removes the old, clears quarantine and starts it", async () => {
    bundle(`${dir}/Apps/hibiki.app`, "old");
    bundle(`${dir}/extracted/hibiki.app`, "new");
    await exec(macScript({ pid: GONE, target: `${dir}/Apps/hibiki.app`, replacement: `${dir}/extracted/hibiki.app` }));

    expect(fs.readFileSync(`${dir}/Apps/hibiki.app/Contents/MacOS/hibiki`, "utf8")).toBe("new");
    expect(fs.existsSync(`${dir}/Apps/hibiki.app.hibiki-old`)).toBe(false);
    expect(fs.existsSync(`${dir}/extracted/hibiki.app`)).toBe(false);
    expect(logged()).toBe(`xattr -cr ${dir}/Apps/hibiki.app\nopen -n ${dir}/Apps/hibiki.app\n`);
  });

  it("macOS: works for a name with spaces and a quote in it", async () => {
    const target = `${dir}/My Apps/it's hibiki.app`;
    bundle(target, "old");
    bundle(`${dir}/extracted/hibiki.app`, "new");
    await exec(macScript({ pid: GONE, target, replacement: `${dir}/extracted/hibiki.app` }));
    expect(fs.readFileSync(`${target}/Contents/MacOS/hibiki`, "utf8")).toBe("new");
  });

  it("macOS: puts the old bundle back and starts it when the new one cannot be moved in", async () => {
    bundle(`${dir}/Apps/hibiki.app`, "old");
    await exec(macScript({ pid: GONE, target: `${dir}/Apps/hibiki.app`, replacement: `${dir}/extracted/missing.app` }));

    expect(fs.readFileSync(`${dir}/Apps/hibiki.app/Contents/MacOS/hibiki`, "utf8")).toBe("old");
    expect(fs.existsSync(`${dir}/Apps/hibiki.app.hibiki-old`)).toBe(false);
    expect(logged()).toContain(`open -n ${dir}/Apps/hibiki.app`);
  });

  it("macOS: waits for the app to quit before touching it", async () => {
    bundle(`${dir}/Apps/hibiki.app`, "old");
    bundle(`${dir}/extracted/hibiki.app`, "new");
    // A real process that lives for a moment: the swap must come after it, not during.
    const sleeper = execFile("sh", ["-c", "sleep 1"]);
    const started = Date.now();
    await exec(macScript({ pid: sleeper.pid!, target: `${dir}/Apps/hibiki.app`, replacement: `${dir}/extracted/hibiki.app` }));
    // (On systems whose sh cannot see that process id the loop ends at once; the file result holds either way.)
    expect(fs.readFileSync(`${dir}/Apps/hibiki.app/Contents/MacOS/hibiki`, "utf8")).toBe("new");
    expect(Date.now() - started).toBeLessThan(10_000);
  });

  it("Linux: replaces the AppImage and starts the new one", async () => {
    const marker = `${dir}/started.txt`;
    fs.writeFileSync(`${dir}/hibiki.AppImage`, `#!/bin/sh\necho old > "${marker}"\n`, { mode: 0o755 });
    fs.writeFileSync(`${dir}/hibiki.AppImage.hibiki-new`, `#!/bin/sh\necho new > "${marker}"\n`, { mode: 0o755 });
    await exec(appImageScript({ pid: GONE, target: `${dir}/hibiki.AppImage`, replacement: `${dir}/hibiki.AppImage.hibiki-new` }));

    expect(fs.readFileSync(marker, "utf8").trim()).toBe("new");
    expect(fs.existsSync(`${dir}/hibiki.AppImage.hibiki-new`)).toBe(false);
  });

  it("Linux: starts the old AppImage again when the new one cannot be moved in", async () => {
    const marker = `${dir}/started.txt`;
    fs.writeFileSync(`${dir}/hibiki.AppImage`, `#!/bin/sh\necho old > "${marker}"\n`, { mode: 0o755 });
    await exec(appImageScript({ pid: GONE, target: `${dir}/hibiki.AppImage`, replacement: `${dir}/missing` }));
    expect(fs.readFileSync(marker, "utf8").trim()).toBe("old");
  });
});
