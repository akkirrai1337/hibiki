package org.akkirrai.beakokit.api

/** Creates the platform bridge for one validated package module. */
fun interface ExternalSourceRuntimeNativeBridgeFactory {
    fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
        module: ByteArray,
        hostRequirements: SourceHostRequirements,
    ): ExternalSourceRuntimeNativeBridge
}

/** Builds the common protocol runtime after loading the package module through the platform boundary. */
class NativeBridgeExternalSourceRuntimeFactory(
    private val bridgeFactory: ExternalSourceRuntimeNativeBridgeFactory,
    private val moduleReader: SourcePackageModuleReader,
    private val requestIdFactory: () -> String,
    private val runtimeSupportPolicy: SourceRuntimeSupportPolicy = SourceRuntimeSupportPolicy.WASMTIME_WASI,
    private val payloadCodec: ExternalSourceRuntimePayloadCodec = AnimeTitleRuntimePayloadCodec,
    private val callLimits: ExternalSourceRuntimeCallLimits = ExternalSourceRuntimeCallLimits(),
) : ExternalSourceRuntimeFactory {
    override fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
    ): ExternalSourceRuntime {
        val runtime = sourcePackage.manifest.runtime
        if (!runtimeSupportPolicy.supports(runtime)) {
            throw SourcePackageValidationException(
                listOf("Unsupported source runtime: ${runtime.id}/${runtime.abi}"),
            )
        }
        val transport = NativeBridgeExternalSourceRuntimeTransport(
                bridge = bridgeFactory.create(
                    sourcePackage = sourcePackage,
                    context = context,
                    module = moduleReader.read(
                        packagePath = sourcePackage.installed.packagePath,
                        entrypoint = sourcePackage.manifest.entrypoint,
                    ),
                    hostRequirements = sourcePackage.manifest.hostRequirements(),
                ),
            )
        val declaresLatest = SourceCapability.LATEST_RELEASES in sourcePackage.manifest.capabilities
        val declaresPlayback = SourceCapability.PLAYBACK in sourcePackage.manifest.capabilities
        return when {
            declaresLatest && declaresPlayback -> ProtocolBackedExternalSourceLatestPlaybackRuntime(
                transport = transport,
                payloadCodec = requirePlaybackCodec(),
                requestIdFactory = requestIdFactory,
                callLimits = callLimits,
            )
            declaresLatest -> ProtocolBackedExternalSourceLatestRuntime(
                transport = transport,
                payloadCodec = payloadCodec,
                requestIdFactory = requestIdFactory,
                callLimits = callLimits,
            )
            declaresPlayback -> ProtocolBackedExternalSourcePlaybackRuntime(
                transport = transport,
                payloadCodec = requirePlaybackCodec(),
                requestIdFactory = requestIdFactory,
                callLimits = callLimits,
            )
            else -> ProtocolBackedExternalSourceRuntime(
                transport = transport,
                payloadCodec = payloadCodec,
                requestIdFactory = requestIdFactory,
                callLimits = callLimits,
            )
        }
    }

    private fun requirePlaybackCodec(): ExternalSourcePlaybackRuntimePayloadCodec =
        payloadCodec as? ExternalSourcePlaybackRuntimePayloadCodec
            ?: throw SourcePackageValidationException(
                listOf("Playback source requires a playback-capable runtime payload codec"),
            )
}
