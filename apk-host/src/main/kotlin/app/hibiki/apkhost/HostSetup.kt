package app.hibiki.apkhost

import android.app.Application
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.hasFactory
import java.io.File

/**
 * Registers what APK sources take from the host: they compile these as compileOnly and fetch them
 * through Injekt.get()/injectLazy(), exactly as inside Aniyomi - the same set the Android build
 * registers (app/hibiki/HibikiApplication.kt).
 */
@OptIn(ExperimentalSerializationApi::class)
object HostSetup {
    lateinit var application: Application
        private set

    @Synchronized
    fun initialize(dataDir: File): Application {
        if (::application.isInitialized) return application
        val context = HostContext(dataDir)
        application = HostApplication(context)
        Injekt.addSingleton<Application>(application)
        if (!Injekt.hasFactory<Json>()) {
            Injekt.addSingleton(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        if (!Injekt.hasFactory<ProtoBuf>()) Injekt.addSingleton(ProtoBuf {})
        NetworkHelper.initialize(application)
        Injekt.addSingleton(NetworkHelper.instance())
        Injekt.addSingleton(JavaScriptEngine(application))
        return application
    }
}
