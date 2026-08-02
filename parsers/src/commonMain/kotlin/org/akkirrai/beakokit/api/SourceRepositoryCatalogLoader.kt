package org.akkirrai.beakokit.api

/** A successfully loaded index together with the endpoint it came from. */
data class LoadedSourceRepository(
    val endpoint: SourceRepositoryEndpoint,
    val index: SourceRepositoryIndex,
)

/** A repository that could not be loaded without invalidating other repositories. */
data class SourceRepositoryLoadFailure(
    val endpoint: SourceRepositoryEndpoint,
    val error: SourceRepositoryLoadException,
)

data class SourceRepositoryLoadSnapshot(
    val loaded: List<LoadedSourceRepository>,
    val failures: List<SourceRepositoryLoadFailure>,
)

/** Loads configured repositories independently so one unavailable endpoint does not hide others. */
class SourceRepositoryCatalogLoader(
    private val catalog: SourceRepositoryCatalog,
    private val loader: SourceRepositoryLoader,
) {
    suspend fun loadAll(
        clientVersion: Int,
        supportedSourceApiVersion: Int = SourceApi.VERSION,
        supportedHostApiVersion: Int = SourceHostApi.VERSION,
    ): SourceRepositoryLoadSnapshot {
        val loaded = mutableListOf<LoadedSourceRepository>()
        val failures = mutableListOf<SourceRepositoryLoadFailure>()
        catalog.load().forEach { endpoint ->
            try {
                loaded += LoadedSourceRepository(
                    endpoint = endpoint,
                    index = loader.load(
                        url = endpoint.url,
                        clientVersion = clientVersion,
                        supportedSourceApiVersion = supportedSourceApiVersion,
                        supportedHostApiVersion = supportedHostApiVersion,
                    ),
                )
            } catch (error: SourceRepositoryLoadException) {
                failures += SourceRepositoryLoadFailure(endpoint, error)
            }
        }
        return SourceRepositoryLoadSnapshot(loaded = loaded, failures = failures)
    }
}
