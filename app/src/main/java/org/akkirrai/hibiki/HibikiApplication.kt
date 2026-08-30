package org.akkirrai.hibiki

import android.app.Application
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import java.io.File

class HibikiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AnimeSourceRegistry.initialize(File(filesDir, "extensions"))
    }
}
