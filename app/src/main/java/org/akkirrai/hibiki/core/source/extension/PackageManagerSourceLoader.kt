package org.akkirrai.hibiki.core.source.extension

import android.content.Context
import dalvik.system.DexClassLoader
import org.akkirrai.beakokit.api.AnimeSource
import org.akkirrai.beakokit.api.SourceContext

/**
 * Loads the [AnimeSource] implementation named by a [DiscoveredSourceExtension] from its
 * installed APK. Not yet wired into [org.akkirrai.beakokit.api.SourceCatalog] registration --
 * this is the Phase A checkpoint proving discovery + load works end to end.
 */
object PackageManagerSourceLoader {
    fun load(
        androidContext: Context,
        extension: DiscoveredSourceExtension,
        sourceContext: SourceContext,
    ): AnimeSource {
        val optimizedDirectory = androidContext.getDir("hibiki-source-dex", Context.MODE_PRIVATE)
        val classLoader = DexClassLoader(
            extension.apkPath,
            optimizedDirectory.absolutePath,
            null,
            androidContext.classLoader,
        )
        val sourceClass = classLoader.loadClass(extension.sourceClassName)
        val constructor = sourceClass.getConstructor(SourceContext::class.java)
        return constructor.newInstance(sourceContext) as AnimeSource
    }
}
