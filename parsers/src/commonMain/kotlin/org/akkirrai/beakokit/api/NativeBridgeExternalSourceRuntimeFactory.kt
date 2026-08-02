package org.akkirrai.beakokit.api

/** Creates the platform bridge for one validated package path. */
fun interface ExternalSourceRuntimeNativeBridgeFactory {
    fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
    ): ExternalSourceRuntimeNativeBridge
}

/** Builds the common protocol runtime while leaving module loading to the platform bridge. */
class NativeBridgeExternalSourceRuntimeFactory(
    private val bridgeFactory: ExternalSourceRuntimeNativeBridgeFactory,
    private val requestIdFactory: () -> String,
    private val payloadCodec: ExternalSourceRuntimePayloadCodec = AnimeTitleRuntimePayloadCodec,
    private val callLimits: ExternalSourceRuntimeCallLimits = ExternalSourceRuntimeCallLimits(),
) : ExternalSourceRuntimeFactory {
    override fun create(
        sourcePackage: ActiveExternalSourcePackage,
        context: SourceContext,
    ): ExternalSourceRuntime = ProtocolBackedExternalSourceRuntime(
        transport = NativeBridgeExternalSourceRuntimeTransport(
            bridge = bridgeFactory.create(sourcePackage, context),
        ),
        payloadCodec = payloadCodec,
        requestIdFactory = requestIdFactory,
        callLimits = callLimits,
    )
}
