package org.akkirrai.hibiki

import android.app.Application
import org.akkirrai.hibiki.app.di.HibikiDependencies
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import java.io.File

class HibikiApplication : Application() {
    val dependencies: HibikiDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HibikiDependencies(this)
    }

    override fun onCreate() {
        super.onCreate()
        AnimeSourceRegistry.initialize(this, File(filesDir, "extensions"))
    }
}
