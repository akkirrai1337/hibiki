# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Standalone source-extension APKs are compiled against this API and loaded at
# runtime through DexClassLoader, resolving any class it shares with the host
# (BeakoKit itself, plus every runtime library both sides link against) via
# parent-classloader delegation into the host's own minified copy. Mirrors
# Mihon's extension-lib rules for the same plugin-loading architecture:
# no renaming at all (-dontobfuscate) and allowoptimization on every package
# an extension can reach into, since a plain -keep alone still let R8 merge
# away Kotlin's default-argument constructor overloads and unused-looking
# stdlib helpers (Intrinsics.checkNotNullExpressionValue, etc.) that only
# extension bytecode - invisible to R8 at host build time - calls directly.
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

# ktor's IntelliJ debugger-detection helper references JVM-only reflection
# APIs that don't exist on Android; harmless (the code path never runs there),
# but keeping io.ktor.** above makes R8 actually resolve it instead of
# silently dropping the reference.
-dontwarn java.lang.management.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

