package org.akkirrai.hibiki

import android.app.Application
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.akkirrai.hibiki.app.di.HibikiDependencies
import org.akkirrai.hibiki.core.source.AnimeSourceRegistry
import eu.kanade.tachiyomi.network.JavaScriptEngine
import org.akkirrai.hibiki.core.model.ReleaseStatusText
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.hasFactory
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
class HibikiApplication : Application() {
    val dependencies: HibikiDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HibikiDependencies(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Extensions compile these libraries as compileOnly and resolve them from the host APK.
        if (!Injekt.hasFactory<Application>()) {
            Injekt.addSingleton<Application>(this)
        }
        if (!Injekt.hasFactory<Json>()) {
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
        if (!Injekt.hasFactory<ProtoBuf>()) {
            Injekt.addSingleton(ProtoBuf {})
        }
        ReleaseStatusText.initialize(this)
        NetworkHelper.initialize(this)
        // Extensions fetch these through Injekt.get()/injectLazy() exactly as in Aniyomi.
        if (!Injekt.hasFactory<NetworkHelper>()) {
            Injekt.addSingleton(NetworkHelper.instance())
        }
        if (!Injekt.hasFactory<JavaScriptEngine>()) {
            Injekt.addSingleton(JavaScriptEngine(this))
        }
        AnimeSourceRegistry.initialize(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            // Extensions installed outside Hibiki are matched with the stored repository indexes first (no
            // network), as the Sources screen would, so they are usable from the start.
            withContext(Dispatchers.IO) {
                runCatching { org.akkirrai.hibiki.core.source.extension.ExternalApkExtensions.sync(this@HibikiApplication) }
            }
            AnimeSourceRegistry.refreshApkExtensions(this@HibikiApplication)
        }
    }
}
