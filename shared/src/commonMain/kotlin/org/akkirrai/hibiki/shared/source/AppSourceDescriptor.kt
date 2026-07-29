package org.akkirrai.hibiki.shared.source

/** Platform-neutral source metadata consumed by the shared Sources UI. */
data class AppSourceDescriptor(
    val id: String,
    val name: String,
    val language: String,
    val iconUrl: String? = null,
    val supportsPlayback: Boolean = false,
)
