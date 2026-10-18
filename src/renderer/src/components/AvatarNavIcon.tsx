import { useProfileStore } from "@/stores/profileStore";
import { cn } from "@/lib/cn";

/**
 * The profile's place in the navigation drawn as its avatar - the person's own face reads at once
 * where a generic person glyph does not. Without an avatar, the usual icon (`fallback`). Lit, it
 * gets an accent ring, the way the other entries turn accent.
 */
export function AvatarNavIcon({ active, className, fallback }: {
  active: boolean;
  /** Size, matching the icons beside it. */
  className: string;
  fallback: React.ReactNode;
}) {
  const avatar = useProfileStore((s) => s.avatarDataUrl);
  if (!avatar) return <>{fallback}</>;
  return (
    <img
      src={avatar}
      alt=""
      draggable={false}
      className={cn(
        "block rounded-full object-cover transition-shadow",
        // A transparent gap between the ring and the face, whatever is behind (the bar, the sidebar).
        active ? "ring-[1.5px] ring-accent-text ring-offset-[1.5px] ring-offset-transparent" : "ring-1 ring-text/15",
        className,
      )}
    />
  );
}
