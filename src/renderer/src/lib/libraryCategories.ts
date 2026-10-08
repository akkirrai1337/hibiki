import { Eye, Clock, CheckCircle2, XCircle, PauseCircle, Heart } from "lucide-react";
import type { LibraryCategory } from "@shared/types";

// Mirrors the Android app's LibraryCategory ordering (Watching, Planned, Completed, Dropped,
// OnHold, Favorite). Offline downloads live on their own dedicated screen (see routes/downloads.tsx)
// rather than as a library category - downloads are tracked per-episode, not per-title, so they
// never fit the library's own per-title card grid the way the rest of these do.
export const ALL_LIBRARY_CATEGORIES: LibraryCategory[] = ["watching", "planned", "completed", "dropped", "on_hold", "favorite"];

export const ASSIGNABLE_LIBRARY_CATEGORIES: LibraryCategory[] = ALL_LIBRARY_CATEGORIES;

export const LIBRARY_CATEGORY_ICONS: Record<LibraryCategory, typeof Eye> = {
  watching: Eye,
  planned: Clock,
  completed: CheckCircle2,
  dropped: XCircle,
  on_hold: PauseCircle,
  favorite: Heart,
};

export const LIBRARY_CATEGORY_LABEL_KEYS: Record<LibraryCategory, string> = {
  watching: "library.categories.watching",
  planned: "library.categories.planned",
  completed: "library.categories.completed",
  dropped: "library.categories.dropped",
  on_hold: "library.categories.on_hold",
  favorite: "library.categories.favorite",
};
