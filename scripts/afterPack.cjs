// electron-builder afterPack hook: gives the macOS app an ad-hoc signature.
//
// There is no Apple Developer certificate, so electron-builder leaves the app unsigned - but it has
// by then changed the bundle (renamed the executable, edited Info.plist, added the app's files), which
// leaves the seal Electron shipped with invalid. An app with a broken seal that was downloaded in a
// browser is "damaged" to Gatekeeper, with no "open anyway"; one with a valid ad-hoc seal is merely
// from an unidentified developer, which the person can allow (see the README). Signing ad hoc ("-")
// needs no certificate.
//
// Best effort: if it cannot be done the build carries on as it did before this hook existed.
const { execFileSync } = require("node:child_process");
const path = require("node:path");

exports.default = async function afterPack(context) {
  if (context.electronPlatformName !== "darwin") return;
  const app = path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`);
  try {
    execFileSync("codesign", ["--force", "--deep", "--sign", "-", app], { stdio: "inherit" });
    execFileSync("codesign", ["--verify", "--deep", "--strict", app], { stdio: "inherit" });
    console.log(`  • ad-hoc signed ${app}`);
  } catch (error) {
    console.warn(`  • ad-hoc signing skipped: ${error instanceof Error ? error.message : error}`);
  }
};
