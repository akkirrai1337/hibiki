import { create } from "zustand";
import { persist } from "zustand/middleware";

// Purely local/device profile - there's no account system (mirrors the Android app, which also
// has no login). Name and avatar are just cosmetic and persisted the same way as the rest of the
// app's UI prefs (localStorage), not the SQLite DB - nothing else needs to read them.
interface ProfileState {
  name: string | null;
  avatarDataUrl: string | null;
  // Just the banner's filename (e.g. "banner.gif") - its bytes live on disk, written by
  // hibiki.profile.setBanner (see main/ipc/profileBanner.ts), since a GIF or video is too big to
  // keep as a `data:` URL in this store's own localStorage backing the way the avatar is.
  bannerFilename: string | null;
  setName: (name: string | null) => void;
  setAvatarDataUrl: (avatarDataUrl: string | null) => void;
  setBannerFilename: (bannerFilename: string | null) => void;
}

export const useProfileStore = create<ProfileState>()(
  persist(
    (set) => ({
      name: null,
      avatarDataUrl: null,
      bannerFilename: null,
      setName: (name) => set({ name }),
      setAvatarDataUrl: (avatarDataUrl) => set({ avatarDataUrl }),
      setBannerFilename: (bannerFilename) => set({ bannerFilename }),
    }),
    { name: "hibiki-profile" },
  ),
);
