import { useEffect, useRef, useState } from "react";
import { useRouter } from "@tanstack/react-router";
import { ChevronLeft, ChevronRight, RotateCw, Minus, Square, Copy, X } from "lucide-react";
import { hibiki } from "@/lib/hibiki";
import { useUiStore } from "@/stores/uiStore";
import { useSpotlightStore } from "@/stores/spotlightStore";
import { UpdateButton } from "@/components/UpdateButton";
import { cn } from "@/lib/cn";
import appIcon from "@/assets/app-icon.png";

// Windows/Linux: the native title bar is hidden entirely (see main/index.ts's titleBarStyle:
// "hidden") and this draws everything, minimize/maximize/close included (see WindowControls below)
// - not Electron's titleBarOverlay (Window Controls Overlay), which technically also works but
// always paints a solid, opaque rectangle behind the OS-drawn buttons that a gradient "background
// theme" (see lib/theme.ts) couldn't follow. Plain page content follows app theming exactly like
// everything else. macOS keeps its normal system title bar (close/minimize/zoom, drag - see createWindow in
// main/index.ts) and this bar sits below it as the app's own: navigation, search, updates. WindowControls
// only renders on other platforms.
export function TitleBar() {
  const router = useRouter();
  const [canGoBack, setCanGoBack] = useState(router.history.canGoBack());

  // The router's history can say whether there is somewhere to go back to, but not forward - the
  // browser deliberately hides that. Its own entry index is enough to work it out: a push discards
  // everything ahead of it (so the newest index is the furthest one), going back leaves the furthest
  // index where it was, and there is something ahead whenever the current entry is short of it.
  const [canGoForward, setCanGoForward] = useState(false);
  const furthestIndex = useRef(router.history.location.state.__TSR_index ?? 0);
  useEffect(() => router.history.subscribe(({ location, action }) => {
    const index = location.state.__TSR_index ?? 0;
    furthestIndex.current = action.type === "PUSH" ? index : Math.max(furthestIndex.current, index);
    setCanGoBack(router.history.canGoBack());
    setCanGoForward(index < furthestIndex.current);
  }), [router]);

  // There's nothing yet to search, and nowhere else to jump "home" to, while onboarding still owns
  // the whole screen (see __root.tsx) - both would just be dead chrome floating over it.
  const onboardingCompleted = useUiStore((s) => s.onboardingCompleted);

  // Quick search over whatever is on screen (see SearchSpotlight): Ctrl/Cmd+K anywhere, or "/" when the
  // cursor is not already in a field. The button next to it is the way to the Search page itself.
  useEffect(() => {
    if (!onboardingCompleted) return;
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const typing = !!target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.isContentEditable);
      // By the physical key (`code`), not the character (`key`): on a Russian or Ukrainian layout the K key
      // types "л" / "л" and the slash key types ".", so matching characters worked on English only.
      const plain = !event.ctrlKey && !event.metaKey && !event.altKey && !event.shiftKey;
      const shortcut = ((event.ctrlKey || event.metaKey) && !event.altKey && event.code === "KeyK") || (event.code === "Slash" && plain && !typing);
      if (!shortcut) return;
      event.preventDefault();
      useSpotlightStore.getState().toggle();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onboardingCompleted]);

  // `-webkit-app-region: drag` correctly detects the drag region even while maximized, but Windows
  // never actually engages the "unmaximize and follow the cursor" behavior a real titlebar gives
  // for free - nothing happens at all. Unmaximizing ourselves first (synchronously, before
  // Chromium's own drag-intent handling for this same mousedown can decide "maximized, nothing to
  // do") fixes that specific case; a genuinely empty click on the bar itself is the only thing that
  // should trigger it, not a click that landed on the search box or a button (those already work
  // fine and shouldn't unmaximize as a side effect).
  // On macOS the system title bar above this one is what moves and maximizes the window; this bar is the app's own.
  const nativeFrame = hibiki.platform === "darwin";
  const onBarMouseDown = (e: React.MouseEvent) => {
    if (!nativeFrame && e.target === e.currentTarget) hibiki.window.unmaximizeForDrag(e.screenX);
  };
  // A real OS title bar toggles maximize/restore on a double-click anywhere on the bar itself -
  // free with a native frame, but a fully custom one (see the comment up top for why this isn't
  // titleBarOverlay) gets none of that automatically, so it's wired up by hand here. Same
  // `e.target === e.currentTarget` guard as the drag handler above, so double-clicking the search
  // box or a nav button doesn't also toggle the window.
  const onBarDoubleClick = (e: React.MouseEvent) => {
    if (!nativeFrame && e.target === e.currentTarget) hibiki.window.toggleMaximize();
  };

  return (
    <div className="app-drag relative flex h-10 shrink-0 items-center bg-app-surface pl-3" onMouseDown={onBarMouseDown} onDoubleClick={onBarDoubleClick}>
      <div className="flex shrink-0 items-center gap-1">
        <img src={appIcon} alt="" className="mr-1.5 h-6 w-6 rounded-[7px]" />
        <button
          onClick={() => router.history.back()}
          disabled={!canGoBack}
          aria-label="Back"
          className={cn("app-no-drag flex h-6 w-6 items-center justify-center rounded-full transition-colors", canGoBack ? "text-muted hover:bg-text/10 hover:text-text" : "cursor-default text-muted/50")}
        >
          <ChevronLeft className="h-4 w-4" strokeWidth={2.5} />
        </button>
        <button
          onClick={() => router.history.forward()}
          disabled={!canGoForward}
          aria-label="Forward"
          className={cn("app-no-drag flex h-6 w-6 items-center justify-center rounded-full transition-colors", canGoForward ? "text-muted hover:bg-text/10 hover:text-text" : "cursor-default text-muted/50")}
        >
          <ChevronRight className="h-4 w-4" strokeWidth={2.5} />
        </button>
        <button
          onClick={() => window.location.reload()}
          aria-label="Reload"
          title="Reload (Ctrl+R)"
          className="app-no-drag flex h-6 w-6 items-center justify-center rounded-full text-muted transition-colors hover:bg-text/10 hover:text-text"
        >
          <RotateCw className="h-[15px] w-[15px]" strokeWidth={2.25} />
        </button>
      </div>

      {/* One right-aligned cluster, not two independently right-aligned items. Flexbox splits the
          free space *equally* between every auto margin in the row, so giving both this and
          WindowControls their own `ml-auto` parked the update pill halfway across the bar, on top
          of the centred search box. UpdateButton renders nothing at all unless there is actually
          a newer release, in which case this collapses to just the window controls. */}
      <div className="ml-auto flex h-full shrink-0 items-center">
        <UpdateButton />
        <WindowControls />
      </div>
    </div>
  );
}

// Custom minimize/maximize/close - see the comment at the top of this file for why these exist
// instead of Electron's own titleBarOverlay buttons. `ml-auto` (not relying on `justify-between`
// on the parent) so this sits flush against the right edge regardless of whether the search box
// next to it is hidden (it's absolutely positioned and centers on the whole window either way, so
// it never actually pushes this over via normal flex layout). UpdateButton carries an `ml-auto`
// too: whichever of the two renders first absorbs the free space, so they stay adjacent at the
// right whether or not there's an update to show.
function WindowControls() {
  const [maximized, setMaximized] = useState(false);
  useEffect(() => {
    hibiki.window.isMaximized().then(setMaximized);
    return hibiki.window.onMaximizedChanged(setMaximized);
  }, []);

  // Only Windows/Linux - macOS keeps its native hiddenInset traffic lights (see createWindow in
  // main/index.ts), which already sit top-left and would collide with a second set drawn here.
  if (hibiki.platform === "darwin") return null;

  return (
    <div className="app-no-drag ml-auto flex h-full shrink-0 items-stretch">
      <button
        onClick={() => hibiki.window.minimize()}
        aria-label="Minimize"
        className="flex w-11 items-center justify-center text-muted transition-colors hover:bg-text/[.08] hover:text-text"
      >
        <Minus className="h-4 w-4" strokeWidth={2} />
      </button>
      <button
        onClick={() => hibiki.window.toggleMaximize()}
        aria-label={maximized ? "Restore" : "Maximize"}
        className="flex w-11 items-center justify-center text-muted transition-colors hover:bg-text/[.08] hover:text-text"
      >
        {maximized ? <Copy className="h-[13px] w-[13px] -scale-x-100" strokeWidth={2} /> : <Square className="h-[13px] w-[13px]" strokeWidth={2} />}
      </button>
      <button
        onClick={() => hibiki.window.close()}
        aria-label="Close"
        className="flex w-11 items-center justify-center text-muted transition-colors hover:bg-rose-500 hover:text-white"
      >
        <X className="h-[18px] w-[18px]" strokeWidth={2} />
      </button>
    </div>
  );
}
