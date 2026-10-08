package app.hibiki

import android.app.Application
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.hasFactory

/**
 * Registers what APK sources (Aniyomi extensions) take from the host app: they compile these as
 * compileOnly and fetch them through Injekt.get()/injectLazy(), exactly as inside Aniyomi - the same
 * set the Kotlin Hibiki app registers.
 */
@OptIn(ExperimentalSerializationApi::class)
class HibikiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Injekt.hasFactory<Application>()) Injekt.addSingleton<Application>(this)
        if (!Injekt.hasFactory<Json>()) {
            Injekt.addSingleton(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        if (!Injekt.hasFactory<ProtoBuf>()) Injekt.addSingleton(ProtoBuf {})
        NetworkHelper.initialize(this)
        if (!Injekt.hasFactory<NetworkHelper>()) Injekt.addSingleton(NetworkHelper.instance())
        if (!Injekt.hasFactory<JavaScriptEngine>()) Injekt.addSingleton(JavaScriptEngine(this))
    }
}
