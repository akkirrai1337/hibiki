package org.akkirrai.beakokit.api

/**
 * Platform-neutral routing for requests sent from an external source to the host.
 *
 * The platform supplies the actual HTTP operation. Capability and URL policy remain enforced by
 * that platform-owned client before the operation reaches the network.
 */
class ExternalSourceHostDispatcher(
    private val executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
    private val storage: ExternalSourceHostStorageAccess?,
    private val cookies: SourceHostCookiesAccess?,
) {
    constructor(
        executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
    ) : this(executeHttpRequest, null)

    constructor(
        executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
        storage: ExternalSourceHostStorageAccess?,
    ) : this(executeHttpRequest, storage, null)

    suspend fun dispatch(request: ExternalSourceHostRequest): ExternalSourceHostResponse {
        val payload = when (request.operation) {
            ExternalSourceHostOperation.HTTP_REQUEST -> {
                val httpRequest = ExternalSourceHostProtocolCodec.decodeHttpRequest(request.payload)
                ExternalSourceHostProtocolCodec.encodeHttpResponse(executeHttpRequest(httpRequest))
            }
            ExternalSourceHostOperation.STORAGE_READ -> {
                val storageRequest = ExternalSourceHostProtocolCodec.decodeStorageReadRequest(request.payload)
                SourceHostStorage.requireKey(storageRequest.key)
                val value = requireStorage().read(storageRequest.key)
                ExternalSourceHostProtocolCodec.encodeStorageReadResponse(
                    ExternalSourceHostStorageReadResponse(value),
                )
            }
            ExternalSourceHostOperation.STORAGE_WRITE -> {
                val storageRequest = ExternalSourceHostProtocolCodec.decodeStorageWriteRequest(request.payload)
                SourceHostStorage.requireKey(storageRequest.key)
                SourceHostStorage.requireValue(storageRequest.value)
                requireStorage().write(storageRequest.key, storageRequest.value)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.STORAGE_REMOVE -> {
                val storageRequest = ExternalSourceHostProtocolCodec.decodeStorageRemoveRequest(request.payload)
                SourceHostStorage.requireKey(storageRequest.key)
                requireStorage().remove(storageRequest.key)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.COOKIES_FOR_URL -> {
                val cookiesRequest = ExternalSourceHostProtocolCodec.decodeCookiesForUrlRequest(request.payload)
                val cookies = requireCookies().forUrl(cookiesRequest.url)
                ExternalSourceHostProtocolCodec.encodeCookiesForUrlResponse(
                    ExternalSourceHostCookiesForUrlResponse(cookies),
                )
            }
            ExternalSourceHostOperation.COOKIES_STORE_RESPONSE -> {
                val cookiesRequest =
                    ExternalSourceHostProtocolCodec.decodeCookiesStoreResponseRequest(request.payload)
                requireCookies().storeFromResponse(cookiesRequest.url, cookiesRequest.cookies)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.COOKIES_CLEAR -> {
                val cookiesRequest = ExternalSourceHostProtocolCodec.decodeCookiesClearRequest(request.payload)
                requireCookies().clear(cookiesRequest.url)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
        }
        return ExternalSourceHostResponse(
            requestId = request.requestId,
            payload = payload,
        )
    }

    private fun requireStorage(): ExternalSourceHostStorageAccess =
        storage ?: throw SourceHostCapabilityException(SourceHostCapability.STORAGE)

    private fun requireCookies(): SourceHostCookiesAccess =
        cookies ?: throw SourceHostCapabilityException(SourceHostCapability.COOKIES)
}

/** Source-scoped storage exposed by the platform host to the protocol dispatcher. */
interface ExternalSourceHostStorageAccess {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    suspend fun remove(key: String)
}

/** Source-scoped cookies exposed by the platform host to the protocol dispatcher. */
interface SourceHostCookiesAccess {
    suspend fun forUrl(url: String): Map<String, String>

    suspend fun storeFromResponse(url: String, cookies: Map<String, String>)

    suspend fun clear(url: String)
}
