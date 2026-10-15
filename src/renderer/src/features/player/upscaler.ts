// Anime4K on the GPU: the player's frames go through WebGPU compute shaders made for anime line
// art (sharper lines, fewer compression artifacts) and are drawn, upscaled, on a canvas over the
// <video>. The point is traffic: a 720p stream looks close to 1080p, so the lighter stream is
// enough. anime4k-webgpu supplies the shader pipelines; the frame loop is ours, because the
// package's own render() can neither be stopped nor follow a quality switch.
//
// Only a picture under 1080p is processed - above that there is nothing worth the GPU time - and
// anything that goes wrong (no WebGPU, a lost device) ends in the plain <video>, never in a black
// screen.
/// <reference types="@webgpu/types" />

export type UpscaleMode = "off" | "light" | "strong";

/** Processed only below this height. */
const MAX_SOURCE_HEIGHT = 1079;

export function isWebGpuAvailable(): boolean {
  return typeof navigator !== "undefined" && "gpu" in navigator && !!navigator.gpu;
}

// A triangle that covers the canvas, sampling the pipeline's output texture.
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

export interface UpscalerHandle {
  stop(): void;
}

/**
 * Starts drawing `video` through Anime4K onto `canvas`. `onActive(true)` once frames are being
 * drawn (the caller then shows the canvas), `onActive(false)` whenever the plain video should show
 * instead (a source at 1080p or more, an error). Resolves once the GPU is set up; rejects when
 * WebGPU is missing.
 */
export async function startUpscaler(
  video: HTMLVideoElement,
  canvas: HTMLCanvasElement,
  mode: Exclude<UpscaleMode, "off">,
  onActive: (active: boolean) => void,
  log: (message: string) => void,
): Promise<UpscalerHandle> {
  if (!isWebGpuAvailable()) throw new Error("WebGPU is not available");
  const anime4k = await import("anime4k-webgpu");
  const adapter = await navigator.gpu.requestAdapter({ powerPreference: "high-performance" });
  if (!adapter) throw new Error("no WebGPU adapter");
  const device = await adapter.requestDevice();
  const context = canvas.getContext("webgpu");
  if (!context) throw new Error("no WebGPU canvas context");
  const format = navigator.gpu.getPreferredCanvasFormat();
  context.configure({ device, format, alphaMode: "opaque" });

  const sampler = device.createSampler({ magFilter: "linear", minFilter: "linear" });
  const shader = device.createShaderModule({ code: BLIT_WGSL });
  const blit = device.createRenderPipeline({
    layout: "auto",
    vertex: { module: shader, entryPoint: "vert_main" },
    fragment: { module: shader, entryPoint: "frag_main", targets: [{ format }] },
    primitive: { topology: "triangle-list" },
  });

  let stopped = false;
  let frameHandle = 0;
  let built: { width: number; height: number; input: GPUTexture; pipelines: Array<{ pass(e: GPUCommandEncoder): void }>; bindGroup: GPUBindGroup } | null = null;
  let active = false;
  const setActive = (next: boolean) => {
    if (next !== active) { active = next; onActive(next); }
  };

  const build = (width: number, height: number) => {
    built?.input.destroy();
    const input = device.createTexture({
      size: [width, height, 1],
      format: "rgba16float",
      usage: GPUTextureUsage.TEXTURE_BINDING | GPUTextureUsage.COPY_DST | GPUTextureUsage.RENDER_ATTACHMENT,
    });
    // Light: one medium CNN doubling pass. Strong: Anime4K's preset A (restore, then upscale).
    const pipeline = mode === "light"
      ? new anime4k.CNNx2M({ device, inputTexture: input })
      : new anime4k.ModeA({ device, inputTexture: input, nativeDimensions: { width, height }, targetDimensions: { width: width * 2, height: height * 2 } });
    const output = pipeline.getOutputTexture();
    canvas.width = output.width;
    canvas.height = output.height;
    const bindGroup = device.createBindGroup({
      layout: blit.getBindGroupLayout(0),
      entries: [{ binding: 0, resource: sampler }, { binding: 1, resource: output.createView() }],
    });
    built = { width, height, input, pipelines: [pipeline], bindGroup };
    log(`upscaler: ${mode} ${width}x${height} -> ${output.width}x${output.height}`);
  };

  const frame = () => {
    if (stopped) return;
    try {
      const width = video.videoWidth;
      const height = video.videoHeight;
      if (width > 0 && height > 0 && height <= MAX_SOURCE_HEIGHT) {
        if (!built || built.width !== width || built.height !== height) build(width, height);
        device.queue.copyExternalImageToTexture({ source: video }, { texture: built!.input }, [width, height]);
        const encoder = device.createCommandEncoder();
        for (const pipeline of built!.pipelines) pipeline.pass(encoder);
        const pass = encoder.beginRenderPass({
          colorAttachments: [{ view: context.getCurrentTexture().createView(), clearValue: { r: 0, g: 0, b: 0, a: 1 }, loadOp: "clear", storeOp: "store" }],
        });
        pass.setPipeline(blit);
        pass.setBindGroup(0, built!.bindGroup);
        pass.draw(3);
        pass.end();
        device.queue.submit([encoder.finish()]);
        // The player zooms/crops the video with a transform (mobile fill, letterbox crop): follow it.
        canvas.style.transform = video.style.transform;
        setActive(true);
      } else {
        setActive(false);
      }
    } catch (error) {
      log(`upscaler: frame failed, showing the plain video (${error instanceof Error ? error.message : String(error)})`);
      stop();
      return;
    }
    frameHandle = video.requestVideoFrameCallback(frame);
  };

  const stop = () => {
    if (stopped) return;
    stopped = true;
    video.cancelVideoFrameCallback(frameHandle);
    setActive(false);
    built?.input.destroy();
    try { context.unconfigure(); } catch { /* already gone */ }
    device.destroy();
  };

  void device.lost.then((info) => {
    if (!stopped) log(`upscaler: GPU device lost (${info.message}), showing the plain video`);
    stop();
  });

  // A paused video sends no frames: draw the one on screen now (frame() then keeps itself going).
  if (video.readyState >= video.HAVE_CURRENT_DATA) frame();
  else frameHandle = video.requestVideoFrameCallback(frame);
  return { stop };
}
