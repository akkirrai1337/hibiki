package org.akkirrai.beakokit.api

import org.akkirrai.beakokit.model.CatalogCapabilities

/** Metadata and runtime factory for one installed external source. */
data class ExternalSourceRegistration(
    val info: SourceInfo,
    val catalogCapabilities: CatalogCapabilities,
    val runtimeFactory: (SourceContext) -> ExternalSourceRuntime,
    val registrationOrder: Int? = null,
) {
    fun catalogEntry(): SourceCatalogEntry = SourceCatalogEntry(
        info = info,
        registrationOrder = registrationOrder,
        factory = SourceFactory { context ->
            info.configSchema.requireValid(context.config)
            val runtime = runtimeFactory(context)
            val declaresPlayback = SourceCapability.PLAYBACK in info.capabilities
            val declaresLatest = SourceCapability.LATEST_RELEASES in info.capabilities
            when {
                declaresPlayback && declaresLatest -> {
                    require(runtime is ExternalSourceLatestPlaybackRuntime) {
                        "Source ${info.id} declares PLAYBACK and LATEST_RELEASES but its runtime does not implement both"
                    }
                    RuntimeBackedLatestPlaybackAnimeSource(
                        info = info,
                        catalogCapabilities = catalogCapabilities,
                        runtime = runtime,
                    )
                }
                declaresPlayback -> {
                    require(runtime is ExternalSourcePlaybackRuntime) {
                        "Source ${info.id} declares PLAYBACK but its runtime does not implement playback"
                    }
                    RuntimeBackedPlaybackAnimeSource(
                        info = info,
                        catalogCapabilities = catalogCapabilities,
                        runtime = runtime,
                    )
                }
                declaresLatest -> {
                    require(runtime is ExternalSourceLatestRuntime) {
                        "Source ${info.id} declares LATEST_RELEASES but its runtime does not implement latest"
                    }
                    RuntimeBackedLatestAnimeSource(
                        info = info,
                        catalogCapabilities = catalogCapabilities,
                        runtime = runtime,
                    )
                }
                else -> RuntimeBackedAnimeSource(
                    info = info,
                    catalogCapabilities = catalogCapabilities,
                    runtime = runtime,
                )
            }
        },
    )
}

fun externalSourceCatalog(
    registrations: Iterable<ExternalSourceRegistration>,
): SourceCatalog = SourceCatalog(registrations.map(ExternalSourceRegistration::catalogEntry))

fun externalSourceRegistry(
    registrations: Iterable<ExternalSourceRegistration>,
): ExternalSourceRegistry = ExternalSourceRegistry(externalSourceCatalog(registrations))

fun activeExternalSourceRegistry(
    packages: Iterable<ActiveExternalSourcePackage>,
    catalogCapabilities: (SourceManifest) -> CatalogCapabilities,
    runtimeFactory: ExternalSourceRuntimeFactory,
): ExternalSourceRegistry = externalSourceRegistry(
    packages.map { sourcePackage ->
        sourcePackage.toExternalSourceRegistration(
            catalogCapabilities = catalogCapabilities(sourcePackage.manifest),
            runtimeFactory = runtimeFactory,
        )
    },
)

/** A validated manifest paired with the package version selected for execution. */
data class ActiveExternalSourcePackage(
    val manifest: SourceManifest,
    val installed: InstalledSourcePackage,
) {
    init {
        require(manifest.sourceId == installed.sourceId) {
            "Manifest source ID does not match the installed package"
        }
        require(manifest.packageVersion == installed.packageVersion) {
            "Manifest package version does not match the installed package"
        }
        require(installed.packagePath.isNotBlank()) { "Installed package path must not be blank" }
    }

    fun toExternalSourceRegistration(
        catalogCapabilities: CatalogCapabilities,
        runtimeFactory: ExternalSourceRuntimeFactory,
        registrationOrder: Int? = null,
    ): ExternalSourceRegistration = manifest.toExternalSourceRegistration(
        catalogCapabilities = catalogCapabilities,
        runtimeFactory = { context -> runtimeFactory.create(this, context) },
        registrationOrder = registrationOrder,
    )
}

fun SourceManifest.toExternalSourceRegistration(
    catalogCapabilities: CatalogCapabilities,
    runtimeFactory: (SourceContext) -> ExternalSourceRuntime,
    registrationOrder: Int? = null,
): ExternalSourceRegistration = ExternalSourceRegistration(
    info = requireSourceInfo(),
    catalogCapabilities = catalogCapabilities,
    runtimeFactory = runtimeFactory,
    registrationOrder = registrationOrder,
)
