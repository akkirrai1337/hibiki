package org.akkirrai.hibiki.shared.app.destination.context

import org.akkirrai.hibiki.shared.library.LibraryRepository
import org.akkirrai.hibiki.shared.profile.LocalProfileDataRepository
import org.akkirrai.hibiki.shared.settings.LanguageMode

internal data class AppDestinationDataContext(
    val libraryRepository: LibraryRepository,
    val profileRepository: LocalProfileDataRepository,
    val languageMode: LanguageMode,
)
