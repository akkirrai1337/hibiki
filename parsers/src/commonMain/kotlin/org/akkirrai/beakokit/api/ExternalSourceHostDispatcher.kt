package org.akkirrai.beakokit.api

/**
 * Platform-neutral routing for requests sent from an external source to the host.
 *
 * The platform supplies the actual HTTP operation. Capability and URL policy remain enforced by
 * that platform-owned client before the operation reaches the network.
 */
class ExternalSourceHostDispatcher(
    private val executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
) {
    suspend fun dispatch(request: ExternalSourceHostRequest): ExternalSourceHostResponse {
        val payload = when (request.operation) {
            ExternalSourceHostOperation.HTTP_REQUEST -> {
                val httpRequest = ExternalSourceHostProtocolCodec.decodeHttpRequest(request.payload)
                ExternalSourceHostProtocolCodec.encodeHttpResponse(executeHttpRequest(httpRequest))
            }
        }
        return ExternalSourceHostResponse(
            requestId = request.requestId,
            payload = payload,
        )
    }
}
