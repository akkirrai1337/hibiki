package org.akkirrai.beakokit.api

/** Creates the platform bridge for one validated package module. */
fun interface ExternalSourceRuntimeNativeBridgeFactory {
    fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
        module: ByteArray,
    ): ExternalSourceRuntimeNativeBridge
}

/** Builds the common protocol runtime after loading the package module through the platform boundary. */
class NativeBridgeExternalSourceRuntimeFactory(
    private val bridgeFactory: ExternalSourceRuntimeNativeBridgeFactory,
    private val moduleReader: SourcePackageModuleReader,
    private val requestIdFactory: () -> String,
    private val payloadCodec: ExternalSourceRuntimePayloadCodec = AnimeTitleRuntimePayloadCodec,
    private val callLimits: ExternalSourceRuntimeCallLimits = ExternalSourceRuntimeCallLimits(),
) : ExternalSourceRuntimeFactory {
    override fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
    ): ExternalSourceRuntime = ProtocolBackedExternalSourceRuntime(
        transport = NativeBridgeExternalSourceRuntimeTransport(
            bridge = bridgeFactory.create(
                sourcePackage = sourcePackage,
                context = context,
                module = moduleReader.read(
                    packagePath = sourcePackage.installed.packagePath,
                    entrypoint = sourcePackage.manifest.entrypoint,
                ),
            ),
        ),
        payloadCodec = payloadCodec,
        requestIdFactory = requestIdFactory,
        callLimits = callLimits,
    )
}
