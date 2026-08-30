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
# Rhino discovers its Android-compatible bridge by name at runtime. Keep only that bridge;
# retaining the whole library would also retain Rhino's desktop AWT/Swing debugger.
-keep class org.mozilla.javascript.VMBridge { *; }
-keep class org.mozilla.javascript.jdk18.VMBridge_jdk18 { *; }
-keep class org.mozilla.javascript.jdk18.VMBridge_jdk18$* { *; }
# Context loads the interpreter with Class.forName(), then instantiates it reflectively.
-keep class org.mozilla.javascript.Interpreter { *; }
-keep class org.mozilla.javascript.Interpreter$* { *; }
# Rhino creates both the regexp engine and RegExp constructor from class-name strings.
-keep class org.mozilla.javascript.regexp.** { *; }
-dontwarn java.lang.management.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
