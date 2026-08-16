package org.akkirrai.hibiki.shared.source

import androidx.compose.runtime.staticCompositionLocalOf

/** Background external-source coordinator supplied by a platform host when available. */
val LocalExternalSourceRuntimeCoordinator =
    staticCompositionLocalOf<ExternalSourceRuntimeCoordinator?> { null }
