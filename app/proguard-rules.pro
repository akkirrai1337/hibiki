-dontobfuscate
-keep,allowoptimization class org.akkirrai.beakokit.** { *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class kotlinx.datetime.** { public protected *; }
-keep,allowoptimization class io.ktor.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
# JNI reaches into QuickJS members by name, including private ones.
-keep class app.cash.quickjs.** { *; }
# Aniyomi extension APKs are loaded at runtime and link against this ABI by name; R8 cannot
# see their references, so anything it considers unused would surface as NoSuchMethodError.
-keep class eu.kanade.tachiyomi.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class rx.** { public protected *; }
-keep class androidx.preference.** { public protected *; }
-dontwarn rx.internal.**
# Desktop-only JDK classes referenced (but never reached) by ktor and other JVM libraries.
-dontwarn java.lang.management.**
-dontwarn java.beans.**
