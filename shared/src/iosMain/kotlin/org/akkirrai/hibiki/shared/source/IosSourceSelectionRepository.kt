package org.akkirrai.hibiki.shared.source

import platform.Foundation.NSUserDefaults

internal class IosSourceSelectionRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SourceSelectionRepository {
    override fun loadSelectedSourceId(): String? = defaults.stringForKey(SELECTED_SOURCE_KEY)

    override fun saveSelectedSourceId(sourceId: String) {
        defaults.setObject(sourceId, forKey = SELECTED_SOURCE_KEY)
    }

    private companion object {
        const val SELECTED_SOURCE_KEY = "hibiki.source.selected"
    }
}
