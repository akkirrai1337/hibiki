import type { HibikiApi } from "@shared/hibikiApi";
import { getPlatform } from "../platform";

// A profile banner (an image, GIF, or short video the user picks for their own profile page) is
// only ever cosmetic and local, but can be several MB - too big to keep as a `data:` URL in
// localStorage the way the avatar is (see profileStore.ts). Written once to a small dedicated
// folder (platform.paths.profile) instead, with only its filename (not its bytes) round-tripping
// through the persisted store. Old files are deleted eagerly on replace/clear rather than left to
// pile up, since there is never more than one banner.

// Whatever the picker offers (see AvatarPicker's sibling, BannerPicker, in pages/profile.tsx): a
// still image, an animated GIF, or a short video.
const EXTENSION_BY_MIME: Record<string, string> = {
  "image/gif": "gif",
  "image/png": "png",
  "image/jpeg": "jpg",
  "image/webp": "webp",
  "video/mp4": "mp4",
};

async function clearExisting(): Promise<void> {
  const { files, paths } = getPlatform();
  let entries: string[];
  try {
    entries = await files.list(paths.profile);
  } catch {
    return;
  }
  // "banner." is how older versions named it, "banner-<time>." how they are named now.
  await Promise.all(entries.filter((name) => name.startsWith("banner.") || name.startsWith("banner-")).map((name) => files.remove(files.join(paths.profile, name))));
}

/** The profile-banner part of `window.hibiki`. */
export function createProfileApi(): HibikiApi["profile"] {
  return {
    async setBanner(bytes: ArrayBuffer, mimeType: string): Promise<string> {
      const extension = EXTENSION_BY_MIME[mimeType];
      if (!extension) throw new Error(`Unsupported banner type: ${mimeType}`);
      const { files, paths } = getPlatform();
      await files.mkdir(paths.profile);
      await clearExisting();
      // A new name every time: the same name for a new picture left the persisted filename (and so
      // the page) unchanged, and the image cached under its old URL - replacing a banner did nothing.
      const filename = `banner-${Date.now()}.${extension}`;
      await files.writeBytes(files.join(paths.profile, filename), new Uint8Array(bytes));
      return filename;
    },

    async clearBanner(): Promise<void> {
      await clearExisting();
    },
  };
}
