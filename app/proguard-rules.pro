# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Standalone source-extension APKs are compiled against this API and loaded at
# runtime through DexClassLoader. Keep the complete BeakoKit ABI stable so an
# extension resolves the same classes and members in a minified host build.
# Mirrors Mihon's extension-lib rules for the same plugin-loading architecture:
# no renaming at all (-dontobfuscate) and allowoptimization on the shared API
# package, since a plain -keep alone still let R8 merge away Kotlin's
# default-argument constructor overloads that extensions call directly.
-dontobfuscate
-keep,allowoptimization class org.akkirrai.beakokit.** { *; }

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

