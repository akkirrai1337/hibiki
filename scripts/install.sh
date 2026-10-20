#!/bin/sh
# Installs hibiki on macOS or Linux from the latest GitHub release:
#
#   curl -fsSL https://raw.githubusercontent.com/akkirrai1337/hibiki/main/scripts/install.sh | sh
#
# It reads the release's update-manifest.json (the same file the app updates itself from) to find the
# file for this system, checks its SHA-256, and installs it:
#   macOS  - hibiki.app into /Applications (or ~/Applications when that is not writable)
#   Linux  - the AppImage as ~/.local/bin/hibiki, with a menu entry
#
# A file fetched by curl is not marked as downloaded from the internet, so macOS does not stop the
# app on first launch the way it does one saved from a browser.
#
# Settings, for the unusual: HIBIKI_PREFIX (where to install), HIBIKI_MANIFEST and HIBIKI_RELEASE_BASE
# (another release to install; used by the tests), HIBIKI_OS and HIBIKI_ARCH (pretend to be another system).
set -eu

REPO="akkirrai1337/hibiki"
MANIFEST_URL="${HIBIKI_MANIFEST:-https://github.com/$REPO/releases/latest/download/update-manifest.json}"

say() { printf '%s\n' "$*"; }
fail() { printf 'hibiki install: %s\n' "$*" >&2; exit 1; }

command -v curl >/dev/null 2>&1 || fail "curl is needed"

case "${HIBIKI_OS:-$(uname -s)}" in
  Darwin | darwin) PLATFORM=darwin ;;
  Linux | linux) PLATFORM=linux ;;
  *) fail "this installer is for macOS and Linux; on Windows use the setup .exe from the releases page" ;;
esac
case "${HIBIKI_ARCH:-$(uname -m)}" in
  arm64 | aarch64) ARCH=arm64 ;;
  x86_64 | amd64 | x64) ARCH=x64 ;;
  *) fail "no build for the processor $(uname -m)" ;;
esac
KEY="$PLATFORM-$ARCH"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT INT TERM

say "Looking up the latest release..."
curl -fsSL "$MANIFEST_URL" -o "$WORK/manifest.json" || fail "could not read the release manifest at $MANIFEST_URL"

# The manifest is written by scripts/make-update-manifest.mjs as indented JSON, one field per line,
# so a field of one platform's entry is read with awk instead of needing jq.
entry() {
  awk -v key="\"$1\":" -v name="\"$2\":" '
    $1 == key && $2 == "{" { inside = 1; next }
    inside && $1 == name { value = $2; gsub(/[",]/, "", value); print value; exit }
    inside && ($1 == "}" || $1 == "},") { inside = 0 }
  ' "$WORK/manifest.json"
}
VERSION="$(awk '$1 == "\"version\":" { v = $2; gsub(/[",]/, "", v); print v; exit }' "$WORK/manifest.json")"
FILE="$(entry "$KEY" file)"
SHA256="$(entry "$KEY" sha256)"
[ -n "$VERSION" ] || fail "the manifest has no version"
[ -n "$FILE" ] && [ -n "$SHA256" ] || fail "release $VERSION has no build for $KEY yet"

BASE="${HIBIKI_RELEASE_BASE:-https://github.com/$REPO/releases/download/v$VERSION}"
say "Downloading hibiki $VERSION for $KEY ($FILE)..."
curl -fL --progress-bar "$BASE/$FILE" -o "$WORK/$FILE" || fail "download failed"

if command -v sha256sum >/dev/null 2>&1; then ACTUAL="$(sha256sum "$WORK/$FILE" | cut -d' ' -f1)"; else ACTUAL="$(shasum -a 256 "$WORK/$FILE" | cut -d' ' -f1)"; fi
[ "$ACTUAL" = "$SHA256" ] || fail "the downloaded file does not match the checksum in the release (got $ACTUAL) - nothing was installed"

if [ "$PLATFORM" = darwin ]; then
  DEST="${HIBIKI_PREFIX:-/Applications}"
  if [ ! -w "$DEST" ]; then DEST="$HOME/Applications"; mkdir -p "$DEST"; fi
  ditto -xk "$WORK/$FILE" "$WORK/app" || fail "could not unpack $FILE"
  APP="$(ls -d "$WORK"/app/*.app 2>/dev/null | head -n 1)"
  [ -n "$APP" ] || fail "the archive has no app in it"
  rm -rf "$DEST/$(basename "$APP")"
  mv "$APP" "$DEST/" || fail "could not move the app into $DEST"
  xattr -cr "$DEST/$(basename "$APP")" 2>/dev/null || true
  say "Installed $DEST/$(basename "$APP") - open it from there or from Launchpad."
else
  DEST="${HIBIKI_PREFIX:-$HOME/.local/bin}"
  mkdir -p "$DEST"
  mv -f "$WORK/$FILE" "$DEST/hibiki" || fail "could not write $DEST/hibiki"
  chmod 755 "$DEST/hibiki"
  # A menu entry, so it is found like any other app.
  APPS="${HIBIKI_APPLICATIONS:-$HOME/.local/share/applications}"
  mkdir -p "$APPS"
  cat > "$APPS/hibiki.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=hibiki
Comment=Anime from the sources you choose
Exec=$DEST/hibiki %U
Terminal=false
Categories=AudioVideo;Video;
MimeType=x-scheme-handler/hibiki;
DESKTOP
  say "Installed $DEST/hibiki and a menu entry."
  case ":$PATH:" in *":$DEST:"*) ;; *) say "Note: $DEST is not in your PATH; start it from the menu or with $DEST/hibiki." ;; esac
  say "If it does not start, an AppImage needs FUSE 2 (Debian/Ubuntu: sudo apt install libfuse2)."
fi
