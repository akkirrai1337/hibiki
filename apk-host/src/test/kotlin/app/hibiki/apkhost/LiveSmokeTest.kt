package app.hibiki.apkhost

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A few real sources, end to end against their live sites: install, popular list, a title's
 * details, its episodes, and the links of the first episode. Switched on with -Dapk.live=true
 * (sites change and go down, so this is a check to run by hand, not a gate). -Dapk.only picks
 * extensions by a regex on the file name.
 */
class LiveSmokeTest {
    private val live = System.getProperty("apk.live") == "true"
    private val samples = System.getProperty("apk.samples")?.let(::File)
    private val only = System.getProperty("apk.only")?.let(::Regex) ?: Regex("animevost|animepahe|animego|anikoto")

    @Test
    fun `popular title episodes and links`() {
        if (!live || samples == null) { println("live smoke test is off (-Dapk.live=true)"); return }
        val data = Files.createTempDirectory("apk-host-live").toFile()
        val host = ApkHost(data)
        val report = StringBuilder()
        var reachedLinks = 0
        for (apk in samples.listFiles { f -> f.extension == "apk" && only.containsMatchIn(f.name) }.orEmpty().sortedBy { it.name }) {
            val line = StringBuilder(apk.name).append(": ")
            try {
                val copy = File(data, "in-${apk.name}")
                apk.copyTo(copy, overwrite = true)
                val installed = host.install(copy, null)
                val extension = host.registry.extensions().first { it.extension.packageName == installed.packageName }
                val adapter = extension.adapters.first()
                runBlocking {
                    val popular = adapter.search(JSONObject().put("limit", 10))
                    line.append("popular=${popular.length()} ")
                    val first = popular.getJSONObject(0)
                    val id = first.getString("id")
                    val details = adapter.getById(id)
                    line.append("title='${details.optString("title")}' ")
                    val groups = adapter.playbackGroups(id)
                    val episodes = groups.getJSONObject(0).getJSONArray("episodes")
                    line.append("episodes=${episodes.length()} ")
                    val episodeId = episodes.getJSONObject(0).getString("id")
                    val links = adapter.playerLinks(id, episodeId)
                    line.append("links=${links.length()} first=${links.getJSONObject(0).optString("url").take(90)}")
                    reachedLinks++
                }
            } catch (error: Throwable) {
                var cause: Throwable = error
                while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
                line.append("FAILED at ${line.length}: ${error.message} <- ${cause.javaClass.simpleName}: ${cause.message} @ ${cause.stackTrace.take(6).joinToString(" | ")}")
            }
            report.appendLine(line)
            println(line)
        }
        File("build").mkdirs()
        File("build/live-report.txt").writeText(report.toString())
        data.deleteRecursively()
        assertTrue(reachedLinks > 0, "no source got as far as playable links:\n$report")
    }
}
