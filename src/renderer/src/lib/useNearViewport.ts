// Whether an element has come close to being seen - for images that should only download then.
//
// `loading="lazy"` was not enough: Chromium starts a lazy image 1250-2500px before it reaches the
// screen, in sideways rows too, so the home page still fetched nearly every poster of every row on
// open. This waits until the element is within a short distance of a visible area - of the page and
// of every scrolling row it sits in (scrollMargin) - and stays true from then on: a poster scrolled
// past keeps its picture instead of downloading it again.
import { useCallback, useState } from "react";

// A little ahead of the edge, so a poster is usually there by the time it scrolls in: about a card
// sideways, about a row of cards down the page.
const ROOT_MARGIN = "300px 0px";
const SCROLL_MARGIN = "300px 160px";

const pending = new Map<Element, () => void>();
let observer: IntersectionObserver | null = null;

function sharedObserver(): IntersectionObserver {
  // One observer for every poster on the page rather than one each.
  observer ??= new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      const reveal = pending.get(entry.target);
      pending.delete(entry.target);
      observer?.unobserve(entry.target);
      reveal?.();
    }
  }, { rootMargin: ROOT_MARGIN, scrollMargin: SCROLL_MARGIN } as IntersectionObserverInit);
  return observer;
}

/** A ref for the element, and whether it has been near the screen yet. */
export function useNearViewport<T extends Element>(): [(element: T | null) => void, boolean] {
  const [near, setNear] = useState(() => typeof IntersectionObserver === "undefined");
  const ref = useCallback((element: T | null) => {
    if (!element || typeof IntersectionObserver === "undefined") return;
    const io = sharedObserver();
    pending.set(element, () => setNear(true));
    io.observe(element);
    // React calls a ref callback's returned cleanup when the element goes away (React 19).
    return () => {
      pending.delete(element);
      io.unobserve(element);
    };
  }, []);
  return [ref, near];
}
