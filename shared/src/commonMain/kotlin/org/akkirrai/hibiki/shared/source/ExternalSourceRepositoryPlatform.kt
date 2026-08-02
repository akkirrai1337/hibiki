package org.akkirrai.hibiki.shared.source

/** Platform-owned external repository services. The caller owns and must close the HTTP client. */
class ExternalSourceRepositoryPlatform(
    val coordinator: ExternalSourceRepositoryCoordinator,
    private val closeResources: () -> Unit,
) {
    fun close() = closeResources()
}
