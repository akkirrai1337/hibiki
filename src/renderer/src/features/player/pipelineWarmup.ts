// anime4k-webgpu builds its ~40 compute pipelines with the synchronous createComputePipeline. In
// Chromium that compiles the shaders on the GPU process's main thread, which also composites the
// page: turning the strong mode on froze every frame on screen - the video included - for seconds,
// even with all of our own work in a worker.
//
// The way around it, without changing the library: build the pipeline once against a recording
// stand-in for the device, which captures every shader, layout and pipeline it asks for; compile
// those with createComputePipelineAsync, which Chromium does off that thread; then build the real
// thing, whose identical pipelines now come out of the device's pipeline cache.
/// <reference types="@webgpu/types" />

interface Recorded {
  bindGroupLayouts: Map<object, GPUBindGroupLayoutDescriptor>;
  pipelineLayouts: Map<object, { bindGroupLayouts: object[] }>;
  shaderModules: Map<object, GPUShaderModuleDescriptor>;
  pipelines: Array<{ layout: object | "auto"; module: object; entryPoint?: string; constants?: Record<string, number> }>;
}

/** A device that records what is asked of it and hands back inert stand-ins. */
function recordingDevice(recorded: Recorded): GPUDevice {
  const texture = (size: GPUExtent3D) => {
    const [width, height] = Array.isArray(size) ? size : [(size as GPUExtent3DDict).width, (size as GPUExtent3DDict).height ?? 1];
    return { width, height, depthOrArrayLayers: 1, createView: () => ({}), destroy: () => {} };
  };
  const queue = { writeBuffer: () => {}, writeTexture: () => {}, submit: () => {}, copyExternalImageToTexture: () => {}, onSubmittedWorkDone: async () => {} };
  const device = {
    queue,
    createBindGroupLayout: (descriptor: GPUBindGroupLayoutDescriptor) => { const token = {}; recorded.bindGroupLayouts.set(token, descriptor); return token; },
    createPipelineLayout: (descriptor: { bindGroupLayouts: object[] }) => { const token = {}; recorded.pipelineLayouts.set(token, descriptor); return token; },
    createShaderModule: (descriptor: GPUShaderModuleDescriptor) => { const token = {}; recorded.shaderModules.set(token, descriptor); return token; },
    createComputePipeline: (descriptor: GPUComputePipelineDescriptor) => {
      recorded.pipelines.push({ layout: descriptor.layout as object | "auto", module: descriptor.compute.module as unknown as object, entryPoint: descriptor.compute.entryPoint, constants: descriptor.compute.constants as Record<string, number> | undefined });
      return { getBindGroupLayout: () => ({}) };
    },
    createRenderPipeline: () => ({ getBindGroupLayout: () => ({}) }),
    createTexture: (descriptor: GPUTextureDescriptor) => texture(descriptor.size),
    createSampler: () => ({}),
    createBindGroup: () => ({}),
    createBuffer: (descriptor: GPUBufferDescriptor) => ({ size: descriptor.size, getMappedRange: () => new ArrayBuffer(descriptor.size), unmap: () => {}, destroy: () => {} }),
    createCommandEncoder: () => ({}),
  };
  return device as unknown as GPUDevice;
}

/**
 * Compiles, asynchronously, every compute pipeline `build` would create on `device`. `build` is
 * called with a stand-in device and a stand-in input texture of the given size; whatever it throws
 * there just means no warm-up (the real build then compiles as before).
 */
export async function warmUpPipelines(
  device: GPUDevice,
  width: number,
  height: number,
  build: (device: GPUDevice, inputTexture: GPUTexture) => unknown,
): Promise<number> {
  const recorded: Recorded = { bindGroupLayouts: new Map(), pipelineLayouts: new Map(), shaderModules: new Map(), pipelines: [] };
  const fake = recordingDevice(recorded);
  try {
    build(fake, fake.createTexture({ size: [width, height, 1], format: "rgba16float", usage: 0 }));
  } catch {
    return 0;
  }
  const bindGroupLayouts = new Map<object, GPUBindGroupLayout>();
  const layoutFor = (token: object | "auto"): GPUPipelineLayout | "auto" => {
    if (token === "auto") return "auto";
    const descriptor = recorded.pipelineLayouts.get(token);
    if (!descriptor) return "auto";
    return device.createPipelineLayout({
      bindGroupLayouts: descriptor.bindGroupLayouts.map((bgl) => {
        let real = bindGroupLayouts.get(bgl);
        if (!real) bindGroupLayouts.set(bgl, (real = device.createBindGroupLayout(recorded.bindGroupLayouts.get(bgl)!)));
        return real;
      }),
    });
  };
  const modules = new Map<object, GPUShaderModule>();
  await Promise.all(
    recorded.pipelines.map((pipeline) => {
      let module = modules.get(pipeline.module);
      if (!module) modules.set(pipeline.module, (module = device.createShaderModule(recorded.shaderModules.get(pipeline.module)!)));
      return device
        .createComputePipelineAsync({ layout: layoutFor(pipeline.layout), compute: { module, entryPoint: pipeline.entryPoint, constants: pipeline.constants } })
        .catch(() => undefined);
    }),
  );
  return recorded.pipelines.length;
}
