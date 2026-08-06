package org.akkirrai.hibiki.shared.source

/** Platform-provided source catalog and configuration surface for shared UI. */
class AppSourcePlatformCallbacks(
    val externalSourceRepositoryController: ExternalSourceRepositoryController? = null,
    val sources: List<AppSourceDescriptor> = emptyList(),
    val sourceConfigContent: AppSourceConfigContent? = null,
    val selectedSourceId: String? = null,
    val onSourceSelected: (String) -> Unit = {},
    val readClipboardText: () -> String? = { null },
    val copyText: (String) -> Unit = {},
)
