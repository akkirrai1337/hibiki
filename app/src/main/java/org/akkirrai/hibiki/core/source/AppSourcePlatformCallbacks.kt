package org.akkirrai.hibiki.core.source

class AppSourcePlatformCallbacks(
    val externalSourceRepositoryController: ExternalSourceRepositoryController? = null,
    val sources: List<AppSourceDescriptor> = emptyList(),
    val sourceConfigContent: AppSourceConfigContent? = null,
    val selectedSourceId: String? = null,
    val onSourceSelected: (String) -> Unit = {},
)
