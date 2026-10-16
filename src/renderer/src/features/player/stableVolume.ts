// Stable volume: the player's sound through a compressor, so explosions and openings don't jump
// out and quiet dialogue isn't lost - the night-time "I keep reaching for the volume" problem.
//
// Web Audio can only ever adopt a media element once (createMediaElementSource), and from then on
// the element plays only through the graph. So the source node is made once per element and kept;
// turning the setting off reconnects it straight to the speakers instead. It is made only once the
// audio context actually runs: a suspended context (no user gesture yet) would leave the episode
// silent, so until then the element keeps playing on its own and the next "play" tries again.
// The <video> loads with crossOrigin="anonymous" and every stream answers with CORS headers
// (desktop playerHeaders.ts, Android's same-origin stream proxy) - a tainted element would play
// silence through Web Audio.
import { log } from "@/lib/log";

interface Graph {
  source: MediaElementAudioSourceNode;
  compressor: DynamicsCompressorNode;
}

let context: AudioContext | null = null;
const graphs = new WeakMap<HTMLMediaElement, Graph>();

function audioContext(): AudioContext | null {
  if (context) return context;
  try {
    context = new AudioContext({ latencyHint: "playback" });
  } catch (error) {
    log.warn("player", "stable volume: no audio context", error);
    return null;
  }
  return context;
}

function connect(graph: Graph, ctx: AudioContext, enabled: boolean): void {
  graph.source.disconnect();
  graph.compressor.disconnect();
  if (enabled) {
    graph.source.connect(graph.compressor);
    graph.compressor.connect(ctx.destination);
  } else {
    graph.source.connect(ctx.destination);
  }
}

async function apply(video: HTMLMediaElement, enabled: boolean): Promise<void> {
  const existing = graphs.get(video);
  if (!existing && !enabled) return;
  const ctx = audioContext();
  if (!ctx) return;
  if (ctx.state !== "running") await ctx.resume().catch(() => undefined);
  if (existing) {
    connect(existing, ctx, enabled);
    return;
  }
  if (ctx.state !== "running") return;
  const compressor = ctx.createDynamicsCompressor();
  // Gentle: loud peaks come down about 4:1 above -28 dB, with a soft knee so it never pumps.
  // Chromium adds its own make-up gain, which is what lifts the quiet parts back up.
  compressor.threshold.value = -28;
  compressor.knee.value = 24;
  compressor.ratio.value = 4;
  compressor.attack.value = 0.005;
  compressor.release.value = 0.25;
  const graph = { source: ctx.createMediaElementSource(video), compressor };
  graphs.set(video, graph);
  connect(graph, ctx, enabled);
  log.info("player", "stable volume on");
}

/** Keeps `video`'s sound going through the compressor while `enabled`; returns the cleanup. */
export function attachStableVolume(video: HTMLMediaElement, enabled: boolean): () => void {
  void apply(video, enabled);
  // The context may only start on a gesture, and a backgrounded page can have it suspended: each
  // play is another chance, and costs nothing once everything is in place.
  const onPlay = () => void apply(video, enabled);
  video.addEventListener("play", onPlay);
  return () => video.removeEventListener("play", onPlay);
}
