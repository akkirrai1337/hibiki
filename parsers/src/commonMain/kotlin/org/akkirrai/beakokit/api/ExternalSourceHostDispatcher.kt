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
    private val config: SourceHostConfigAccess?,
    private val requirements: SourceHostRequirements = SourceHostRequirements(
        capabilities = setOf(SourceHostCapability.NETWORK),
    ),
) {
    constructor(
        executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
    ) : this(executeHttpRequest, null)

    constructor(
        executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
        storage: ExternalSourceHostStorageAccess?,
        requirements: SourceHostRequirements = SourceHostRequirements(
            capabilities = setOf(SourceHostCapability.NETWORK),
        ),
    ) : this(executeHttpRequest, storage, null, null, requirements)

    constructor(
        executeHttpRequest: suspend (ExternalSourceHostHttpRequest) -> ExternalSourceHostHttpResponse,
        storage: ExternalSourceHostStorageAccess?,
        cookies: SourceHostCookiesAccess?,
        requirements: SourceHostRequirements = SourceHostRequirements(
            capabilities = setOf(SourceHostCapability.NETWORK),
        ),
    ) : this(executeHttpRequest, storage, cookies, null, requirements)

    suspend fun dispatch(request: ExternalSourceHostRequest): ExternalSourceHostResponse {
        val payload = when (request.operation) {
            ExternalSourceHostOperation.HTTP_REQUEST -> {
                if (!requirements.requires(SourceHostCapability.NETWORK)) {
                    throw SourceHostCapabilityException(SourceHostCapability.NETWORK)
                }
                val httpRequest = ExternalSourceHostProtocolCodec.decodeHttpRequest(request.payload)
                val response = executeHttpRequest(httpRequest)
                require(response.body.encodeToByteArray().size.toLong() <= httpRequest.maxResponseBytes) {
                    "Host HTTP response exceeds ${httpRequest.maxResponseBytes} bytes"
                }
                ExternalSourceHostProtocolCodec.encodeHttpResponse(response)
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
            ExternalSourceHostOperation.CONFIG_VALUE -> {
                val configRequest = ExternalSourceHostProtocolCodec.decodeConfigRequest(request.payload)
                val configAccess = requireConfig()
                ExternalSourceHostProtocolCodec.encodeConfigResponse(
                    ExternalSourceHostConfigResponse(configAccess.value(configRequest.key)),
                )
            }
            ExternalSourceHostOperation.CONFIG_SECRET -> {
                val configRequest = ExternalSourceHostProtocolCodec.decodeConfigRequest(request.payload)
                val configAccess = requireConfig()
                ExternalSourceHostProtocolCodec.encodeConfigResponse(
                    ExternalSourceHostConfigResponse(configAccess.secret(configRequest.key)),
                )
            }
        }
        return ExternalSourceHostResponse(
            requestId = request.requestId,
            payload = payload,
        )
    }

    private fun requireStorage(): ExternalSourceHostStorageAccess {
        requireDeclared(SourceHostCapability.STORAGE)
        return storage ?: throw SourceHostCapabilityException(SourceHostCapability.STORAGE)
    }

    private fun requireCookies(): SourceHostCookiesAccess {
        requireDeclared(SourceHostCapability.COOKIES)
        return cookies ?: throw SourceHostCapabilityException(SourceHostCapability.COOKIES)
    }

    private fun requireConfig(): SourceHostConfigAccess {
        requireDeclared(SourceHostCapability.CONFIG)
        return config ?: throw SourceHostCapabilityException(SourceHostCapability.CONFIG)
    }

    private fun requireDeclared(capability: SourceHostCapability) {
        if (!requirements.requires(capability)) {
            throw SourceHostCapabilityException(capability)
        }
    }
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

/** Source configuration exposed by the platform host to the protocol dispatcher. */
interface SourceHostConfigAccess : SourceHostAccess {
    fun value(key: String): String?

    fun secret(key: String): String?
}
