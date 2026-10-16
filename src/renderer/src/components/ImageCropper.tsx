import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { motion } from "motion/react";
import { useTranslation } from "react-i18next";
import { Check, RotateCw, X } from "lucide-react";
import { useBackHandler } from "@/lib/mobile";

const MAX_ZOOM = 5;
const FRAME_MAX = 520;
const FRAME_GUTTER = 16;

type Offset = { x: number; y: number };

/**
 * A picked picture framed into a square before it becomes the avatar, the way a messenger's photo
 * editor does it: drag to move, pinch / wheel / the slider to zoom (100% = the picture just covers
 * the frame), a quarter turn at a time. ✓ hands back the framed square as a `size`px WebP data URL.
 */
export function ImageCropper({ src, size = 512, onCancel, onDone }: { src: string; size?: number; onCancel: () => void; onDone: (dataUrl: string) => void }) {
  const { t } = useTranslation();
  const stageRef = useRef<HTMLDivElement>(null);
  const [image, setImage] = useState<HTMLImageElement | null>(null);
  const [frame, setFrame] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [offset, setOffset] = useState<Offset>({ x: 0, y: 0 });
  useBackHandler(true, onCancel);

  useEffect(() => {
    const img = new Image();
    img.onload = () => setImage(img);
    img.src = src;
  }, [src]);

  useLayoutEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const measure = () => setFrame(Math.max(0, Math.min(stage.clientWidth - FRAME_GUTTER * 2, stage.clientHeight - FRAME_GUTTER * 2, FRAME_MAX)));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(stage);
    return () => observer.disconnect();
  }, []);

  // The picture's on-screen size as turned (a quarter turn swaps its sides) and its scale.
  const turned = rotation % 180 !== 0;
  const naturalW = image?.naturalWidth ?? 1;
  const naturalH = image?.naturalHeight ?? 1;
  const boxW = turned ? naturalH : naturalW;
  const boxH = turned ? naturalW : naturalH;
  const scaleAt = useCallback((z: number) => (frame / Math.min(boxW, boxH)) * z, [frame, boxW, boxH]);

  // The picture always covers the frame: it can move only as far as its edges allow.
  const clamp = useCallback(
    (o: Offset, z: number): Offset => {
      const s = scaleAt(z);
      const maxX = Math.max(0, (boxW * s - frame) / 2);
      const maxY = Math.max(0, (boxH * s - frame) / 2);
      return { x: Math.min(maxX, Math.max(-maxX, o.x)), y: Math.min(maxY, Math.max(-maxY, o.y)) };
    },
    [scaleAt, boxW, boxH, frame],
  );

  // Keeps the point under `at` (relative to the frame's centre) in place while zooming.
  const zoomTo = (next: number, at: Offset = { x: 0, y: 0 }) => {
    const z = Math.min(MAX_ZOOM, Math.max(1, next));
    setOffset((o) => clamp({ x: at.x - (at.x - o.x) * (z / zoom), y: at.y - (at.y - o.y) * (z / zoom) }, z));
    setZoom(z);
  };

  useEffect(() => setOffset((o) => clamp(o, zoom)), [clamp, zoom]);

  const rotate = () => {
    setRotation((r) => (r + 90) % 360);
    setOffset((o) => ({ x: -o.y, y: o.x }));
  };

  // One finger moves the picture, two pinch it.
  const pointers = useRef(new Map<number, Offset>());
  const pinch = useRef<{ distance: number; zoom: number } | null>(null);
  const relative = (event: { clientX: number; clientY: number }): Offset => {
    const rect = stageRef.current!.getBoundingClientRect();
    return { x: event.clientX - rect.left - rect.width / 2, y: event.clientY - rect.top - rect.height / 2 };
  };
  const onPointerDown = (event: React.PointerEvent) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    pointers.current.set(event.pointerId, relative(event));
    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()];
      pinch.current = { distance: Math.hypot(a.x - b.x, a.y - b.y), zoom };
    }
  };
  const onPointerMove = (event: React.PointerEvent) => {
    const previous = pointers.current.get(event.pointerId);
    if (!previous) return;
    const point = relative(event);
    pointers.current.set(event.pointerId, point);
    if (pointers.current.size === 1) {
      setOffset((o) => clamp({ x: o.x + point.x - previous.x, y: o.y + point.y - previous.y }, zoom));
    } else if (pointers.current.size === 2 && pinch.current) {
      const [a, b] = [...pointers.current.values()];
      const mid = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
      zoomTo(pinch.current.zoom * (Math.hypot(a.x - b.x, a.y - b.y) / pinch.current.distance), mid);
    }
  };
  const onPointerUp = (event: React.PointerEvent) => {
    pointers.current.delete(event.pointerId);
    if (pointers.current.size < 2) pinch.current = null;
  };
  // Native and not passive, so the wheel zooms the picture instead of also scrolling the page under it.
  const onWheel = useRef<(event: WheelEvent) => void>(() => {});
  onWheel.current = (event) => zoomTo(zoom * Math.exp(-event.deltaY * 0.0015), relative(event));
  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    const listener = (event: WheelEvent) => {
      event.preventDefault();
      onWheel.current(event);
    };
    stage.addEventListener("wheel", listener, { passive: false });
    return () => stage.removeEventListener("wheel", listener);
  }, []);

  const save = () => {
    if (!image || !frame) return;
    const canvas = document.createElement("canvas");
    canvas.width = size;
    canvas.height = size;
    const context = canvas.getContext("2d");
    if (!context) return;
    const k = size / frame;
    const s = scaleAt(zoom) * k;
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    context.translate(size / 2 + offset.x * k, size / 2 + offset.y * k);
    context.rotate((rotation * Math.PI) / 180);
    context.drawImage(image, (-naturalW * s) / 2, (-naturalH * s) / 2, naturalW * s, naturalH * s);
    onDone(canvas.toDataURL("image/webp", 0.92));
  };

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCancel();
      if (event.key === "Enter") save();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  const s = scaleAt(zoom);
  return createPortal(
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15 }}
      className="fixed inset-0 z-[95] flex select-none flex-col bg-black text-white"
      style={{ paddingTop: "var(--safe-top)", paddingBottom: "var(--safe-bottom)" }}
    >
      <div className="flex h-14 shrink-0 items-center gap-2 px-2">
        <button type="button" onClick={onCancel} aria-label={t("common.cancel")} className="flex h-11 w-11 items-center justify-center rounded-full transition-colors hover:bg-white/10 active:bg-white/10">
          <X className="h-6 w-6" strokeWidth={2} />
        </button>
        <h2 className="min-w-0 flex-1 truncate text-lg font-semibold">{t("profile.cropTitle")}</h2>
        <button type="button" onClick={save} disabled={!image} aria-label={t("common.save")} className="flex h-11 w-11 items-center justify-center rounded-full text-accent-text transition-colors hover:bg-white/10 active:bg-white/10 disabled:opacity-40">
          <Check className="h-6 w-6" strokeWidth={2.25} />
        </button>
      </div>

      <div
        ref={stageRef}
        className="relative min-h-0 flex-1 cursor-grab touch-none overflow-hidden active:cursor-grabbing"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        {image && frame > 0 && (
          <img
            src={src}
            alt=""
            draggable={false}
            className="pointer-events-none absolute left-1/2 top-1/2 max-w-none"
            style={{
              width: naturalW * s,
              height: naturalH * s,
              transform: `translate(-50%, -50%) translate(${offset.x}px, ${offset.y}px) rotate(${rotation}deg)`,
            }}
          />
        )}
        {frame > 0 && (
          <div
            className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 border border-white/80"
            style={{ width: frame, height: frame, boxShadow: "0 0 0 100vmax rgb(0 0 0 / 0.6)" }}
          >
            <div className="absolute inset-y-0 left-1/3 w-px bg-white/35" />
            <div className="absolute inset-y-0 left-2/3 w-px bg-white/35" />
            <div className="absolute inset-x-0 top-1/3 h-px bg-white/35" />
            <div className="absolute inset-x-0 top-2/3 h-px bg-white/35" />
          </div>
        )}
      </div>

      <div className="mx-auto flex w-full max-w-md shrink-0 flex-col items-center gap-3 px-6 pb-5 pt-4">
        <span className="text-sm font-semibold tabular-nums">{Math.round(zoom * 100)}%</span>
        <div className="flex w-full items-center gap-4">
          <input
            type="range"
            min={1}
            max={MAX_ZOOM}
            step={0.01}
            value={zoom}
            onChange={(event) => zoomTo(Number(event.target.value))}
            aria-label={t("profile.cropZoom")}
            className="h-1 min-w-0 flex-1 cursor-pointer appearance-none rounded-full bg-white/20 accent-accent"
          />
          <button type="button" onClick={rotate} aria-label={t("profile.cropRotate")} title={t("profile.cropRotate")} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors hover:bg-white/10 active:bg-white/10">
            <RotateCw className="h-5 w-5" strokeWidth={2} />
          </button>
        </div>
      </div>
    </motion.div>,
    document.body,
  );
}
