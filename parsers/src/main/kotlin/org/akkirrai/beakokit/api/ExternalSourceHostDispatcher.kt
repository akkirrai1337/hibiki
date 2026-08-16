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
                requireHttpResponseWithinLimit(response.body, httpRequest.maxResponseBytes)
                ExternalSourceHostProtocolCodec.encodeHttpResponse(response)
            }
            ExternalSourceHostOperation.STORAGE_READ -> {
                requireDeclared(SourceHostCapability.STORAGE)
                val storageRequest = ExternalSourceHostProtocolCodec.decodeStorageReadRequest(request.payload)
                SourceHostStorage.requireKey(storageRequest.key)
                val value = requireStorage().read(storageRequest.key)
                value?.let(SourceHostStorage::requireValue)
                ExternalSourceHostProtocolCodec.encodeStorageReadResponse(
                    ExternalSourceHostStorageReadResponse(value),
                )
            }
            ExternalSourceHostOperation.STORAGE_WRITE -> {
                requireDeclared(SourceHostCapability.STORAGE)
                val storageRequest = ExternalSourceHostProtocolCodec.decodeStorageWriteRequest(request.payload)
                SourceHostStorage.requireKey(storageRequest.key)
                SourceHostStorage.requireValue(storageRequest.value)
                requireStorage().write(storageRequest.key, storageRequest.value)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.STORAGE_REMOVE -> {
                requireDeclared(SourceHostCapability.STORAGE)
                val storageRequest = ExternalSourceHostProtocolCodec.decodeStorageRemoveRequest(request.payload)
                SourceHostStorage.requireKey(storageRequest.key)
                requireStorage().remove(storageRequest.key)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.COOKIES_FOR_URL -> {
                requireDeclared(SourceHostCapability.COOKIES)
                val cookiesRequest = ExternalSourceHostProtocolCodec.decodeCookiesForUrlRequest(request.payload)
                SourceHostCookies.requireUrl(cookiesRequest.url)
                val cookies = requireCookies().forUrl(cookiesRequest.url)
                SourceHostCookies.requireCookies(cookies)
                ExternalSourceHostProtocolCodec.encodeCookiesForUrlResponse(
                    ExternalSourceHostCookiesForUrlResponse(cookies),
                )
            }
            ExternalSourceHostOperation.COOKIES_STORE_RESPONSE -> {
                requireDeclared(SourceHostCapability.COOKIES)
                val cookiesRequest =
                    ExternalSourceHostProtocolCodec.decodeCookiesStoreResponseRequest(request.payload)
                SourceHostCookies.requireUrl(cookiesRequest.url)
                SourceHostCookies.requireCookies(cookiesRequest.cookies)
                requireCookies().storeFromResponse(cookiesRequest.url, cookiesRequest.cookies)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.COOKIES_CLEAR -> {
                requireDeclared(SourceHostCapability.COOKIES)
                val cookiesRequest = ExternalSourceHostProtocolCodec.decodeCookiesClearRequest(request.payload)
                SourceHostCookies.requireUrl(cookiesRequest.url)
                requireCookies().clear(cookiesRequest.url)
                ExternalSourceHostProtocolCodec.encodeStorageMutationResponse(
                    ExternalSourceHostStorageMutationResponse(),
                )
            }
            ExternalSourceHostOperation.CONFIG_VALUE -> {
                requireDeclared(SourceHostCapability.CONFIG)
                val configRequest = ExternalSourceHostProtocolCodec.decodeConfigRequest(request.payload)
                SourceHostConfigLimits.requireKey(configRequest.key)
                val configAccess = requireConfig()
                val value = configAccess.value(configRequest.key)
                SourceHostConfigLimits.requireValue(value)
                ExternalSourceHostProtocolCodec.encodeConfigResponse(
                    ExternalSourceHostConfigResponse(value),
                )
            }
            ExternalSourceHostOperation.CONFIG_SECRET -> {
                requireDeclared(SourceHostCapability.CONFIG)
                val configRequest = ExternalSourceHostProtocolCodec.decodeConfigRequest(request.payload)
                SourceHostConfigLimits.requireKey(configRequest.key)
                val configAccess = requireConfig()
                val secret = configAccess.secret(configRequest.key)
                SourceHostConfigLimits.requireValue(secret)
                ExternalSourceHostProtocolCodec.encodeConfigResponse(
                    ExternalSourceHostConfigResponse(secret),
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
        return storage ?: throw SourceHostAdapterUnavailableException(SourceHostCapability.STORAGE)
    }

    private fun requireCookies(): SourceHostCookiesAccess {
        requireDeclared(SourceHostCapability.COOKIES)
        return cookies ?: throw SourceHostAdapterUnavailableException(SourceHostCapability.COOKIES)
    }

    private fun requireConfig(): SourceHostConfigAccess {
        requireDeclared(SourceHostCapability.CONFIG)
        return config ?: throw SourceHostAdapterUnavailableException(SourceHostCapability.CONFIG)
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
