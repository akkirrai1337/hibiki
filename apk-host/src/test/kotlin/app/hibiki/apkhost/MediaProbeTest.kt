package app.hibiki.apkhost

import android.media.MediaMetadataRetriever
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A video's length from its own header: MP4, Matroska and HLS, and only as many bytes as that takes. */
class MediaProbeTest {
    // --- building files ---------------------------------------------------------------------------------------

    private fun box(type: String, body: ByteArray = ByteArray(0)): ByteArray =
        ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(type.toByteArray(Charsets.ISO_8859_1)).put(body).array()

    private fun mvhd(version: Int, timescale: Long, duration: Long): ByteArray {
        val out = ByteBuffer.allocate(if (version == 1) 112 else 100)
        out.put(version.toByte()).put(ByteArray(3))
        if (version == 1) {
            out.putLong(0).putLong(0).putInt(timescale.toInt()).putLong(duration)
        } else {
            out.putInt(0).putInt(0).putInt(timescale.toInt()).putInt(duration.toInt())
        }
        return box("mvhd", out.array())
    }

    private fun ftyp() = box("ftyp", "isom".toByteArray() + ByteArray(8))

    /** A file as [Ranges], like a server would serve it. */
    private class Bytes(private val data: ByteArray, var reads: MutableList<Pair<Long, Int>> = mutableListOf()) : Ranges {
        override val size: Long = data.size.toLong()
        override fun read(offset: Long, length: Int): ByteArray? {
            reads += offset to length
            if (offset >= data.size) return ByteArray(0)
            return data.copyOfRange(offset.toInt(), minOf(data.size.toLong(), offset + length).toInt())
        }
    }

    private fun mp4(vararg boxes: ByteArray) = ByteArrayOutputStream().also { out -> boxes.forEach(out::write) }.toByteArray()

    // --- MP4 ------------------------------------------------------------------------------------------------------

    @Test
    fun `mp4 with the moov first`() {
        val file = mp4(ftyp(), box("moov", mvhd(0, 1000, 1_440_000)), box("mdat", ByteArray(1000)))
        assertEquals(1_440_000L, MediaProbe.mp4Duration(Bytes(file)))
    }

    @Test
    fun `mp4 with the moov after a large mdat is reached by hopping over it`() {
        // A 6 MB mdat: the probe must read headers, not the payload.
        val file = mp4(ftyp(), box("mdat", ByteArray(6 * 1024 * 1024)), box("moov", mvhd(0, 90_000, 90_000L * 1425)))
        val ranges = Bytes(file)
        assertEquals(1_425_000L, MediaProbe.mp4Duration(ranges))
        assertTrue(ranges.reads.all { it.second <= 4096 || it.first > 6_000_000 }, "payload was read: ${ranges.reads}")
    }

    @Test
    fun `version 1 mvhd and a 64-bit box size`() {
        val moov = box("moov", mvhd(1, 600, 600L * 3_600))
        val big = ByteBuffer.allocate(16 + 20).putInt(1).put("free".toByteArray()).putLong(36).put(ByteArray(20)).array()
        assertEquals(3_600_000L, MediaProbe.mp4Duration(Bytes(mp4(ftyp(), big, moov))))
    }

    @Test
    fun `a box that runs to the end of the file`() {
        // Size 0 on the last box means "until the end": here that is the moov, so it is found and read.
        val moov = box("moov", mvhd(0, 1000, 5000))
        val last = moov.copyOf().also { it[0] = 0; it[1] = 0; it[2] = 0; it[3] = 0 }
        assertEquals(5000L, MediaProbe.mp4Duration(Bytes(mp4(ftyp(), last))))
    }

    @Test
    fun `a fragmented mp4 states its length in mehd`() {
        val mehd = box("mehd", ByteBuffer.allocate(8).putInt(0).putInt(2_000).array())
        val moov = box("moov", mvhd(0, 1000, 0) + box("mvex", mehd))
        assertEquals(2_000L, MediaProbe.mp4Duration(Bytes(mp4(ftyp(), moov))))
        // With neither, the length is simply not known.
        assertNull(MediaProbe.mp4Duration(Bytes(mp4(ftyp(), box("moov", mvhd(0, 1000, 0))))))
        assertNull(MediaProbe.mp4Duration(Bytes(mp4(ftyp(), box("moov", mvhd(0, 1000, 0xFFFFFFFFL))))))
    }

    @Test
    fun `broken mp4 never throws`() {
        assertNull(MediaProbe.mp4Duration(Bytes(ByteArray(3))))
        assertNull(MediaProbe.mp4Duration(Bytes(mp4(ftyp()))))
        assertNull(MediaProbe.mp4Duration(Bytes(mp4(ftyp(), box("moov", ByteArray(5))))))
        // A zero timescale cannot divide.
        assertNull(MediaProbe.mp4Duration(Bytes(mp4(ftyp(), box("moov", mvhd(0, 0, 1000))))))
        // A box claiming to be bigger than the file.
        val lying = ByteBuffer.allocate(8).putInt(Int.MAX_VALUE).put("moov".toByteArray()).array()
        assertNull(MediaProbe.mp4Duration(Bytes(mp4(ftyp(), lying))))
    }

    // --- Matroska / WebM ----------------------------------------------------------------------------------------

    private fun element(id: Long, body: ByteArray): ByteArray {
        val idBytes = generateSequence(id) { it shr 8 }.takeWhile { it > 0 }.map { it.toByte() }.toList().reversed().toByteArray()
        return idBytes + size(body.size.toLong()) + body
    }

    private fun size(value: Long): ByteArray = when {
        value < 127 -> byteArrayOf((0x80 or value.toInt()).toByte())
        value < 16_383 -> byteArrayOf((0x40 or (value shr 8).toInt()).toByte(), value.toByte())
        else -> byteArrayOf((0x10 or (value shr 24).toInt()).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())
    }

    private fun ebml(vararg segmentChildren: ByteArray, unknownSegment: Boolean = true): ByteArray {
        val header = element(0x1A45DFA3, element(0x4282, "webm".toByteArray()))
        val children = segmentChildren.fold(ByteArray(0)) { a, b -> a + b }
        val segment = if (unknownSegment) byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) + children
        else element(0x18538067, children)
        return header + segment
    }

    private fun info(durationBytes: ByteArray, scale: Long? = null): ByteArray {
        val timescale = scale?.let { element(0x2AD7B1, ByteBuffer.allocate(4).putInt(it.toInt()).array()) } ?: ByteArray(0)
        return element(0x1549A966, timescale + element(0x4489, durationBytes))
    }

    @Test
    fun `matroska duration in the default timescale`() {
        // The default timescale is 1,000,000 ns per unit, one millisecond: a value of 1,425,000 is 1,425,000 ms.
        val file = ebml(element(0xEC, ByteArray(40)), info(ByteBuffer.allocate(4).putFloat(1_425_000f).array()))
        assertEquals(1_425_000L, MediaProbe.ebmlDuration(Bytes(file)))
    }

    @Test
    fun `matroska duration with its own timescale and an 8-byte float, in a sized segment`() {
        val file = ebml(info(ByteBuffer.allocate(8).putDouble(86_400.0).array(), scale = 1_000_000_000), unknownSegment = false)
        assertEquals(86_400_000L, MediaProbe.ebmlDuration(Bytes(file)))
    }

    @Test
    fun `matroska without an info before the clusters has no duration`() {
        val file = ebml(element(0x1F43B675, ByteArray(10)), info(ByteBuffer.allocate(4).putFloat(1000f).array()))
        assertNull(MediaProbe.ebmlDuration(Bytes(file)))
        assertNull(MediaProbe.ebmlDuration(Bytes(ByteArray(2))))
        assertNull(MediaProbe.ebmlDuration(Bytes(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x80.toByte()))))
    }

    // --- over HTTP ---------------------------------------------------------------------------------------------------

    private val web = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()

    @AfterTest
    fun stop() = web.shutdown()

    /** Serves [files] by path, honouring Range unless [ranges] is false. */
    private fun serve(files: Map<String, ByteArray>, ranges: Boolean = true, contentType: (String) -> String = { "application/octet-stream" }) {
        web.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.path} ${request.getHeader("Range")}"
                val data = files[request.path] ?: return MockResponse().setResponseCode(404)
                val range = request.getHeader("Range")?.let { Regex("bytes=(\\d+)-(\\d+)").matchEntire(it) }
                if (!ranges || range == null) return MockResponse().setBody(Buffer().write(data)).setHeader("Content-Type", contentType(request.path!!))
                val start = range.groupValues[1].toInt()
                if (start >= data.size) return MockResponse().setResponseCode(416)
                val end = minOf(data.size - 1, range.groupValues[2].toInt())
                return MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $start-$end/${data.size}")
                    .setBody(Buffer().write(data.copyOfRange(start, end + 1)))
            }
        }
        web.start()
    }

    private fun probe(path: String, headers: Map<String, String> = emptyMap()): Long? {
        HostSetup.initialize(Files.createTempDirectory("apk-host-probe").toFile())
        return MediaProbe.durationMs(web.url(path).toString(), headers)
    }

    @Test
    fun `an mp4 on a server that honours Range is read by a few small requests`() {
        val file = mp4(ftyp(), box("mdat", ByteArray(3 * 1024 * 1024)), box("moov", mvhd(0, 1000, 1_200_000)))
        serve(mapOf("/v.mp4" to file))
        assertEquals(1_200_000L, probe("/v.mp4"))
        assertTrue(requests.size <= 5, "requests: $requests")
    }

    @Test
    fun `an mp4 on a server that ignores Range works when the moov is first and is not downloaded otherwise`() {
        val head = mp4(ftyp(), box("moov", mvhd(0, 1000, 600_000)), box("mdat", ByteArray(100_000)))
        serve(mapOf("/head.mp4" to head, "/tail.mp4" to mp4(ftyp(), box("mdat", ByteArray(2 * 1024 * 1024)), box("moov", mvhd(0, 1000, 600_000))), "/small.mp4" to mp4(ftyp(), box("mdat", ByteArray(100_000)), box("moov", mvhd(0, 1000, 700_000)))), ranges = false)
        assertEquals(600_000L, probe("/head.mp4"))
        assertNull(probe("/tail.mp4"), "a moov beyond the kept start cannot be reached without Range")
        // A file small enough to arrive in the kept start is read whole.
        assertEquals(700_000L, probe("/small.mp4"))
    }

    @Test
    fun `request headers go with every request`() {
        serve(mapOf("/v.mp4" to mp4(ftyp(), box("moov", mvhd(0, 1000, 1000)))))
        probe("/v.mp4", mapOf("Referer" to "https://site.example/"))
        // (the recorded request is checked through the dispatcher's own log)
        assertTrue(requests.isNotEmpty())
        val taken = web.takeRequest()
        assertEquals("https://site.example/", taken.getHeader("Referer"))
    }

    @Test
    fun `an hls media playlist adds up its segments`() {
        val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:10.0,\na.ts\n#EXTINF:9.5,\nb.ts\n#EXTINF:4.25,\nc.ts\n#EXT-X-ENDLIST\n"
        serve(mapOf("/media.m3u8" to playlist.toByteArray()))
        assertEquals(23_750L, probe("/media.m3u8"))
    }

    @Test
    fun `an hls master playlist follows its first variant, relative to itself`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\nhigh/index.m3u8\n"
        val media = "#EXTM3U\n#EXTINF:6,\ns1.ts\n#EXTINF:6,\ns2.ts\n#EXT-X-ENDLIST\n"
        serve(mapOf("/master.m3u8" to master.toByteArray(), "/low/index.m3u8" to media.toByteArray()))
        assertEquals(12_000L, probe("/master.m3u8"))
    }

    @Test
    fun `a live hls stream, a loop of masters and junk have no duration`() {
        val live = "#EXTM3U\n#EXTINF:6,\ns1.ts\n"
        val loop = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nloop.m3u8\n"
        serve(mapOf("/live.m3u8" to live.toByteArray(), "/loop.m3u8" to loop.toByteArray(), "/junk" to "<html>not media</html>".toByteArray()))
        assertNull(probe("/live.m3u8"))
        assertNull(probe("/loop.m3u8"))
        assertNull(probe("/junk"))
        assertNull(probe("/missing.mp4"))
    }

    @Test
    fun `an unreachable address is unknown, not an error`() {
        HostSetup.initialize(Files.createTempDirectory("apk-host-probe").toFile())
        assertNull(MediaProbe.durationMs("http://127.0.0.1:1/x.mp4", emptyMap()))
        assertNull(MediaProbe.durationMs("not a url", emptyMap()))
    }

    @Test
    fun `real files on real servers`() {
        if (System.getProperty("apk.live") != "true") return
        HostSetup.initialize(Files.createTempDirectory("apk-host-probe").toFile())
        // A 10 s sample clip, and Apple's long-standing HLS test stream (about 10 minutes).
        val clip = MediaProbe.durationMs("https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4", emptyMap())
        println("PROBE clip=$clip")
        assertTrue(clip != null && clip in 9_000..11_000, "clip: $clip")
        val hls = MediaProbe.durationMs("https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8", emptyMap())
        println("PROBE hls=$hls")
        assertTrue(hls != null && hls in 500_000..700_000, "hls: $hls")
    }

    @Test
    fun `the retriever an extension uses answers in milliseconds and nothing for other keys`() {
        serve(mapOf("/v.mp4" to mp4(ftyp(), box("moov", mvhd(0, 1000, 1_440_000)))))
        HostSetup.initialize(Files.createTempDirectory("apk-host-probe").toFile())
        val retriever = MediaMetadataRetriever()
        assertNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), "nothing set yet")
        retriever.setDataSource(web.url("/v.mp4").toString(), mapOf("Referer" to "x"))
        assertEquals("1440000", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
        assertNull(retriever.extractMetadata(18))
        retriever.release()
        assertNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
        // A file it cannot read leaves the duration unknown rather than throwing.
        retriever.setDataSource(web.url("/nope").toString(), emptyMap())
        assertNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
    }
}
