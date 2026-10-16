// Anime4K on the GPU: the player's frames go through WebGPU compute shaders made for anime line
// art (sharper lines, fewer compression artifacts) and are drawn, upscaled, on a canvas over the
// <video>. The point is traffic: a 720p stream looks close to 1080p, so the lighter stream is
// enough.
//
// All the GPU work runs in upscaleWorker.ts on an OffscreenCanvas: done on the UI thread, loading
// the library and compiling its shaders froze the player for seconds on a phone. This side only
// hands the worker one frame at a time - the next only once the last is drawn, so a GPU that can't
// keep up drops frames rather than falling behind - and reports how it is going.
//
// Only a picture under 1080p is processed, and anything that goes wrong (no WebGPU, a lost
// device) ends in the plain <video>, never in a black screen.
import type { FromWorker, ToWorker } from "./upscaleWorker";

export type UpscaleMode = "off" | "light" | "strong";

export type UpscalerState =
  /** Loading the library and setting up the GPU; the plain video plays meanwhile. */
  | "starting"
  /** Enhanced frames are on screen. */
  | "active"
  /** Nothing to do (1080p and up): the plain video shows. */
  | "idle";

export function isWebGpuAvailable(): boolean {
  return (
    typeof navigator !== "undefined" && "gpu" in navigator && !!navigator.gpu &&
    typeof Worker !== "undefined" && typeof OffscreenCanvas !== "undefined" && typeof VideoFrame !== "undefined" &&
    typeof HTMLCanvasElement !== "undefined" && "transferControlToOffscreen" in HTMLCanvasElement.prototype
  );
}

export interface UpscalerCallbacks {
  onState: (state: UpscalerState) => void;
  /** The device can't keep up with this mode: frames are being dropped. */
  onTooSlow: () => void;
  /** Gave up; the plain video shows. */
  onError: (message: string) => void;
  log: (message: string) => void;
}

export interface UpscalerHandle {
  stop(): void;
}

/** Over this share of frames dropped across a few seconds of playback counts as "too slow". */
const MAX_DROP_SHARE = 0.4;
const SLOW_WINDOW_FRAMES = 90;

/**
 * Starts drawing `video` through Anime4K on a canvas it creates inside `host` (an element laid out
 * over the video). Returns at once; progress arrives through the callbacks.
 */
export function startUpscaler(video: HTMLVideoElement, host: HTMLElement, mode: Exclude<UpscaleMode, "off">, callbacks: UpscalerCallbacks): UpscalerHandle {
  const canvas = document.createElement("canvas");
  canvas.setAttribute("aria-hidden", "true");
  canvas.className = "pointer-events-none absolute inset-0 h-full w-full invisible";
  canvas.style.objectFit = "inherit";
  host.appendChild(canvas);

  const worker = new Worker(new URL("./upscaleWorker.ts", import.meta.url), { type: "module" });
  const send = (message: ToWorker, transfer: Transferable[] = []) => worker.postMessage(message, transfer);
  const offscreen = canvas.transferControlToOffscreen();
  send({ type: "init", canvas: offscreen, mode }, [offscreen]);

  let stopped = false;
  let ready = false;
  let busy = false;
  let state: UpscalerState = "starting";
  let frameHandle = 0;
  let presented = 0;
  let dropped = 0;
  let warnedSlow = false;
  callbacks.onState("starting");

  const setState = (next: UpscalerState) => {
    if (next === state) return;
    state = next;
    canvas.classList.toggle("invisible", next !== "active");
    callbacks.onState(next);
  };

  const sendFrame = () => {
    if (stopped || !ready || busy || video.readyState < video.HAVE_CURRENT_DATA) return;
    let frame: VideoFrame;
    try {
      frame = new VideoFrame(video);
    } catch {
      return; // No frame to take yet (seeking, a source switch).
    }
    busy = true;
    // The player zooms/crops the video with a transform (mobile fill, letterbox crop): follow it.
    canvas.style.transform = video.style.transform;
    send({ type: "frame", frame }, [frame]);
  };

  const onVideoFrame = () => {
    if (stopped) return;
    if (busy) dropped++;
    else sendFrame();
    presented++;
    if (presented >= SLOW_WINDOW_FRAMES) {
      if (state === "active" && !warnedSlow && dropped / presented > MAX_DROP_SHARE) {
        warnedSlow = true;
        callbacks.log(`upscaler: ${mode} drops ${Math.round((dropped / presented) * 100)}% of frames on this device`);
        callbacks.onTooSlow();
      }
      presented = 0;
      dropped = 0;
    }
    frameHandle = video.requestVideoFrameCallback(onVideoFrame);
  };

  const stop = () => {
    if (stopped) return;
    stopped = true;
    video.cancelVideoFrameCallback(frameHandle);
    video.removeEventListener("seeked", sendFrame);
    send({ type: "stop" });
    // The worker closes itself after "stop"; terminate in case it is stuck in a GPU call.
    window.setTimeout(() => worker.terminate(), 1000);
    canvas.remove();
    callbacks.onState("idle");
  };

  worker.onmessage = (event: MessageEvent<FromWorker>) => {
    if (stopped) return;
    const message = event.data;
    if (message.type === "ready") {
      ready = true;
      callbacks.log(`upscaler: ${mode} ready`);
      // A paused video sends no frames: draw the one on screen now.
      sendFrame();
    } else if (message.type === "warmed") {
      callbacks.log(`upscaler: ${message.pipelines} shader pipelines compiled in ${message.ms}ms`);
    } else if (message.type === "built") {
      callbacks.log(`upscaler: ${mode} ${message.width}x${message.height} -> ${message.outWidth}x${message.outHeight}`);
    } else if (message.type === "drawn") {
      busy = false;
      setState("active");
    } else if (message.type === "skipped") {
      busy = false;
      setState("idle");
    } else if (message.type === "error") {
      callbacks.log(`upscaler: ${message.message}; showing the plain video`);
      callbacks.onError(message.message);
      stop();
    }
  };
  worker.onerror = (event) => {
    callbacks.log(`upscaler: worker failed (${event.message}); showing the plain video`);
    callbacks.onError(event.message);
    stop();
  };

  frameHandle = video.requestVideoFrameCallback(onVideoFrame);
  // A seek while paused shows a new frame without a frame callback of its own.
  video.addEventListener("seeked", sendFrame);
  return { stop };
}
