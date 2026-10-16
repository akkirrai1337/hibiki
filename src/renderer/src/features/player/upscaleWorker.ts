// The GPU half of upscaler.ts, off the UI thread: loading anime4k-webgpu (3.5 MB), setting up the
// device and compiling its shaders took long enough on a phone to freeze the player for seconds,
// and every frame's GPU submission competed with the page's own work. Here none of that touches
// the UI thread - frames come in as VideoFrames, and the canvas is an OffscreenCanvas this worker
// draws to directly.
/// <reference types="@webgpu/types" />
/// <reference lib="webworker" />
import type { UpscaleMode } from "./upscaler";
import { warmUpPipelines } from "./pipelineWarmup";

export type ToWorker =
  | { type: "init"; canvas: OffscreenCanvas; mode: Exclude<UpscaleMode, "off"> }
  | { type: "frame"; frame: VideoFrame }
  | { type: "stop" };

export type FromWorker =
  | { type: "ready" }
  | { type: "built"; width: number; height: number; outWidth: number; outHeight: number }
  | { type: "warmed"; pipelines: number; ms: number }
  /** A frame was processed; `gpuMs` is how long the GPU took for it. */
  | { type: "drawn"; gpuMs: number }
  /** A frame too large to be worth processing (1080p and up): the plain video shows. */
  | { type: "skipped" }
  | { type: "error"; message: string };

/** Processed only below this height. */
const MAX_SOURCE_HEIGHT = 1079;

const BLIT_WGSL = /* wgsl */ `
struct Out { @builtin(position) pos: vec4f, @location(0) uv: vec2f };
@vertex fn vert_main(@builtin(vertex_index) i: u32) -> Out {
  var p = array<vec2f, 3>(vec2f(-1.0, -1.0), vec2f(3.0, -1.0), vec2f(-1.0, 3.0));
  var o: Out;
  o.pos = vec4f(p[i], 0.0, 1.0);
  o.uv = vec2f((p[i].x + 1.0) * 0.5, (1.0 - p[i].y) * 0.5);
  return o;
}
@group(0) @binding(0) var samp: sampler;
@group(0) @binding(1) var tex: texture_2d<f32>;
@fragment fn frag_main(@location(0) uv: vec2f) -> @location(0) vec4f {
  return vec4f(textureSample(tex, samp, uv).rgb, 1.0);
}`;

const scope = self as unknown as DedicatedWorkerGlobalScope;
const post = (message: FromWorker) => scope.postMessage(message);

interface Gpu {
  anime4k: typeof import("anime4k-webgpu");
  device: GPUDevice;
  context: GPUCanvasContext;
  canvas: OffscreenCanvas;
  blit: GPURenderPipeline;
  sampler: GPUSampler;
  mode: Exclude<UpscaleMode, "off">;
}

let gpu: Gpu | null = null;
let built: { width: number; height: number; input: GPUTexture; pipeline: { pass(e: GPUCommandEncoder): void }; bindGroup: GPUBindGroup } | null = null;
let stopped = false;

async function init(canvas: OffscreenCanvas, mode: Exclude<UpscaleMode, "off">): Promise<void> {
  const anime4k = await import("anime4k-webgpu");
  const adapter = await navigator.gpu.requestAdapter({ powerPreference: "high-performance" });
  if (!adapter) throw new Error("no WebGPU adapter");
  const device = await adapter.requestDevice();
  const context = canvas.getContext("webgpu");
  if (!context) throw new Error("no WebGPU canvas context");
  const format = navigator.gpu.getPreferredCanvasFormat();
  context.configure({ device, format, alphaMode: "opaque" });
  const shader = device.createShaderModule({ code: BLIT_WGSL });
  // Compiled off the critical path; the Anime4K pipelines themselves are built on the first frame.
  const blit = await device.createRenderPipelineAsync({
    layout: "auto",
    vertex: { module: shader, entryPoint: "vert_main" },
    fragment: { module: shader, entryPoint: "frag_main", targets: [{ format }] },
    primitive: { topology: "triangle-list" },
  });
  const sampler = device.createSampler({ magFilter: "linear", minFilter: "linear" });
  void device.lost.then((info) => { if (!stopped) post({ type: "error", message: `GPU device lost (${info.message})` }); });
  gpu = { anime4k, device, context, canvas, blit, sampler, mode };
  post({ type: "ready" });
}

/** Light: one medium CNN doubling pass. Strong: Anime4K's preset A (restore, then upscale). */
function createPipeline(device: GPUDevice, input: GPUTexture, width: number, height: number) {
  const g = gpu!;
  return g.mode === "light"
    ? new g.anime4k.CNNx2M({ device, inputTexture: input })
    : new g.anime4k.ModeA({ device, inputTexture: input, nativeDimensions: { width, height }, targetDimensions: { width: width * 2, height: height * 2 } });
}

async function build(width: number, height: number): Promise<void> {
  const g = gpu!;
  // Shaders compiled asynchronously first, so the real build below doesn't stall the screen.
  const startedAt = performance.now();
  const warmed = await warmUpPipelines(g.device, width, height, (device, input) => createPipeline(device, input, width, height));
  post({ type: "warmed", pipelines: warmed, ms: Math.round(performance.now() - startedAt) });
  if (stopped) return;
  built?.input.destroy();
  const input = g.device.createTexture({
    size: [width, height, 1],
    format: "rgba16float",
    usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT,
  });
  const pipeline = createPipeline(g.device, input, width, height);
  const output = pipeline.getOutputTexture();
  g.canvas.width = output.width;
  g.canvas.height = output.height;
  const bindGroup = g.device.createBindGroup({
    layout: g.blit.getBindGroupLayout(0),
    entries: [{ binding: 0, resource: g.sampler }, { binding: 1, resource: output.createView() }],
  });
  built = { width, height, input, pipeline, bindGroup };
  post({ type: "built", width, height, outWidth: output.width, outHeight: output.height });
}

async function draw(frame: VideoFrame): Promise<void> {
  try {
    const g = gpu;
    if (!g || stopped) return;
    const width = frame.displayWidth;
    const height = frame.displayHeight;
    if (height > MAX_SOURCE_HEIGHT || width === 0) {
      post({ type: "skipped" });
      return;
    }
    if (!built || built.width !== width || built.height !== height) await build(width, height);
    if (stopped || !built) return;
    const b = built!;
    const started = performance.now();
    g.device.queue.copyExternalImageToTexture({ source: frame }, { texture: b.input }, [width, height]);
    const encoder = g.device.createCommandEncoder();
    b.pipeline.pass(encoder);
    const pass = encoder.beginRenderPass({
      colorAttachments: [{ view: g.context.getCurrentTexture().createView(), clearValue: { r: 0, g: 0, b: 0, a: 1 }, loadOp: "clear", storeOp: "store" }],
    });
    pass.setPipeline(g.blit);
    pass.setBindGroup(0, b.bindGroup);
    pass.draw(3);
    pass.end();
    g.device.queue.submit([encoder.finish()]);
    // Waiting for the GPU here is what gives the UI side its backpressure: it sends the next
    // frame only after this "drawn", so a GPU that can't keep up skips frames instead of queueing.
    await g.device.queue.onSubmittedWorkDone();
    post({ type: "drawn", gpuMs: performance.now() - started });
  } finally {
    frame.close();
  }
}

scope.onmessage = (event: MessageEvent<ToWorker>) => {
  const message = event.data;
  if (message.type === "init") {
    init(message.canvas, message.mode).catch((error) => post({ type: "error", message: error instanceof Error ? error.message : String(error) }));
  } else if (message.type === "frame") {
    draw(message.frame).catch((error) => post({ type: "error", message: error instanceof Error ? error.message : String(error) }));
  } else if (message.type === "stop") {
    stopped = true;
    built?.input.destroy();
    try { gpu?.context.unconfigure(); } catch { /* already gone */ }
    gpu?.device.destroy();
    scope.close();
  }
};
