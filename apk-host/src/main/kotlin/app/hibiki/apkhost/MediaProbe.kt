package app.hibiki.apkhost

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** Reads bytes of a media file wherever it lives; [size] is its length when known. */
interface Ranges {
    val size: Long?

    /** Up to [length] bytes at [offset] (fewer at the end of the file), or null when they cannot be had. */
    fun read(offset: Long, length: Int): ByteArray?
}

/**
 * How long a video is, without decoding it: the duration is written in the file's own header (MP4's
 * `mvhd`, Matroska's `Info`) or, for a playlist, adds up from its segments. This is what a source asks
 * Android's MediaMetadataRetriever for. Only the few bytes of header are fetched (HTTP Range), never
 * the video. Null when the format is not one of these or the duration is not stated (a live stream).
 */
object MediaProbe {
    private const val MAX_MOOV = 32 * 1024 * 1024
    private const val MAX_PLAYLIST = 4 * 1024 * 1024
    private const val EBML_WINDOW = 512 * 1024
    private const val MAX_BOX_HOPS = 64
    private const val MAX_HLS_DEPTH = 3

    /** The duration in milliseconds of the media at [url], fetched with [headers]. */
    fun durationMs(url: String, headers: Map<String, String>): Long? = runCatching {
        val client = NetworkHelper.instance().nonCloudflareClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
        duration(url, HttpRanges(client, url, headers), client, headers, 0)
    }.getOrNull()

    internal fun duration(url: String, ranges: Ranges, client: OkHttpClient?, headers: Map<String, String>, depth: Int): Long? {
        val head = ranges.read(0, 4096) ?: return null
        return when {
            head.startsWithText("#EXTM3U") -> if (client == null || depth >= MAX_HLS_DEPTH) null else hlsDuration(url, client, headers, depth)
            head.size >= 12 && String(head, 4, 4, Charsets.ISO_8859_1) in MP4_BRANDS -> mp4Duration(ranges)
            head.size >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> ebmlDuration(ranges)
            else -> null
        }
    }

    private val MP4_BRANDS = setOf("ftyp", "moov", "mdat", "free", "skip", "wide")

    private fun ByteArray.startsWithText(text: String): Boolean {
        // A playlist may begin with a byte order mark.
        val start = if (size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte()) 3 else 0
        return size >= start + text.length && String(this, start, text.length, Charsets.ISO_8859_1) == text
    }

    // --- MP4 / MOV ------------------------------------------------------------------------------------

    /** Walks the top-level boxes (hopping over the big `mdat` by its size) to `moov`, whose `mvhd` holds the duration. */
    fun mp4Duration(ranges: Ranges): Long? {
        var offset = 0L
        repeat(MAX_BOX_HOPS) {
            val head = ranges.read(offset, 16) ?: return null
            if (head.size < 8) return null
            var size = u32(head, 0)
            val type = String(head, 4, 4, Charsets.ISO_8859_1)
            var header = 8
            when (size) {
                1L -> {
                    if (head.size < 16) return null
                    size = u64(head, 8)
                    header = 16
                }
                0L -> size = (ranges.size ?: return null) - offset
            }
            if (size < header) return null
            if (type == "moov") {
                if (size > MAX_MOOV) return null
                val body = ranges.read(offset + header, (size - header).toInt()) ?: return null
                return moovDuration(body)
            }
            offset += size
        }
        return null
    }

    private fun moovDuration(moov: ByteArray): Long? {
        var scale = 0L
        var duration = 0L
        var fragmented = 0L
        forEachBox(moov, 0, moov.size) { type, start, end ->
            when (type) {
                "mvhd" -> mvhd(moov, start, end)?.let { scale = it.first; duration = it.second }
                "mvex" -> forEachBox(moov, start, end) { inner, s, e -> if (inner == "mehd") fragmented = mehd(moov, s, e) }
            }
        }
        if (scale <= 0) return null
        // A fragmented file leaves mvhd's duration empty and says the length in mehd instead.
        return ((if (duration > 0) duration else fragmented) * 1000 / scale).takeIf { it > 0 }
    }

    /** mvhd: version/flags, then (creation, modification, timescale, duration) of 4 bytes each, or 8 for the times and duration in version 1. Returns (timescale, duration). */
    private fun mvhd(b: ByteArray, start: Int, end: Int): Pair<Long, Long>? {
        if (end - start < 20) return null
        if (b[start].toInt() == 1) {
            if (end - start < 32) return null
            return u32(b, start + 20) to u64(b, start + 24)
        }
        val duration = u32(b, start + 16)
        // All ones: not known.
        return u32(b, start + 12) to if (duration == 0xFFFFFFFFL) 0L else duration
    }

    /** mehd (movie extends header): the whole fragmented movie's duration, in mvhd's timescale. */
    private fun mehd(b: ByteArray, start: Int, end: Int): Long {
        if (end - start < 8) return 0
        return if (b[start].toInt() == 1) { if (end - start < 12) 0 else u64(b, start + 4) } else u32(b, start + 4)
    }

    /** Calls [action] with each box's type and the range of its body, between [from] and [to]. */
    private fun forEachBox(b: ByteArray, from: Int, to: Int, action: (type: String, bodyStart: Int, bodyEnd: Int) -> Unit) {
        var pos = from
        while (pos + 8 <= to) {
            var size = u32(b, pos)
            val type = String(b, pos + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (pos + 16 > to) return
                size = u64(b, pos + 8)
                header = 16
            } else if (size == 0L) {
                size = (to - pos).toLong()
            }
            if (size < header || pos + size > to) return
            action(type, pos + header, (pos + size).toInt())
            pos += size.toInt()
        }
    }

    // --- Matroska / WebM --------------------------------------------------------------------------------

    private const val ID_SEGMENT = 0x18538067L
    private const val ID_INFO = 0x1549A966L
    private const val ID_CLUSTER = 0x1F43B675L
    private const val ID_TIMESCALE = 0x2AD7B1L
    private const val ID_DURATION = 0x4489L

    /** Reads the start of the file: the segment's `Info` element carries the duration and the timescale it is counted in. */
    fun ebmlDuration(ranges: Ranges): Long? {
        val window = ranges.read(0, EBML_WINDOW) ?: return null
        var pos = 0
        // EBML header, then the segment; the header is skipped by its size.
        val (_, idLength) = readId(window, pos) ?: return null
        val (headerSize, sizeLength, _) = readSize(window, pos + idLength) ?: return null
        pos += idLength + sizeLength + headerSize.toInt()
        val (segmentId, segmentIdLength) = readId(window, pos) ?: return null
        if (segmentId != ID_SEGMENT) return null
        val (_, segmentSizeLength, _) = readSize(window, pos + segmentIdLength) ?: return null
        pos += segmentIdLength + segmentSizeLength
        while (pos < window.size) {
            val (id, idLen) = readId(window, pos) ?: return null
            val (size, sizeLen, unknown) = readSize(window, pos + idLen) ?: return null
            val body = pos + idLen + sizeLen
            if (id == ID_CLUSTER) return null
            if (id == ID_INFO) {
                if (unknown || body + size > window.size) return null
                return infoDuration(window, body, (body + size).toInt())
            }
            if (unknown) return null
            pos = (body + size).toInt().also { if (it <= pos) return null }
        }
        return null
    }

    private fun infoDuration(b: ByteArray, from: Int, to: Int): Long? {
        var scale = 1_000_000L
        var duration: Double? = null
        var pos = from
        while (pos < to) {
            val (id, idLen) = readId(b, pos) ?: return null
            val (size, sizeLen, unknown) = readSize(b, pos + idLen) ?: return null
            val body = pos + idLen + sizeLen
            if (unknown || body + size > to) return null
            when (id) {
                ID_TIMESCALE -> scale = uint(b, body, size.toInt())
                ID_DURATION -> duration = when (size.toInt()) {
                    4 -> java.lang.Float.intBitsToFloat(uint(b, body, 4).toInt()).toDouble()
                    8 -> java.lang.Double.longBitsToDouble(uint(b, body, 8))
                    else -> null
                }
            }
            pos = (body + size).toInt()
        }
        val value = duration ?: return null
        // The duration is in timescale units, a timescale being nanoseconds per unit.
        return (value * scale / 1_000_000.0).toLong().takeIf { it > 0 }
    }

    /** An element id keeps its length marker bits. Returns (id, length) or null. */
    private fun readId(b: ByteArray, pos: Int): Pair<Long, Int>? {
        if (pos >= b.size) return null
        val first = b[pos].toInt() and 0xFF
        val length = Integer.numberOfLeadingZeros(first) - 24 + 1
        if (length !in 1..4 || pos + length > b.size) return null
        var id = 0L
        for (i in 0 until length) id = (id shl 8) or (b[pos + i].toLong() and 0xFF)
        return id to length
    }

    /** An element size with its marker bit removed: (size, length, unknown). */
    private fun readSize(b: ByteArray, pos: Int): Triple<Long, Int, Boolean>? {
        if (pos >= b.size) return null
        val first = b[pos].toInt() and 0xFF
        if (first == 0) return null
        val length = Integer.numberOfLeadingZeros(first) - 24 + 1
        if (pos + length > b.size) return null
        var value = (first and (0xFF shr length)).toLong()
        var allOnes = value == (0xFF shr length).toLong()
        for (i in 1 until length) {
            val byte = b[pos + i].toInt() and 0xFF
            value = (value shl 8) or byte.toLong()
            if (byte != 0xFF) allOnes = false
        }
        return Triple(value, length, allOnes)
    }

    private fun uint(b: ByteArray, pos: Int, length: Int): Long {
        var value = 0L
        for (i in 0 until length) value = (value shl 8) or (b[pos + i].toLong() and 0xFF)
        return value
    }

    // --- HLS ----------------------------------------------------------------------------------------------

    private fun hlsDuration(url: String, client: OkHttpClient, headers: Map<String, String>, depth: Int): Long? {
        val text = fetchText(client, url, headers) ?: return null
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        // A master playlist lists variants: any of them is the whole video, so the first is asked.
        val variant = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
        if (variant >= 0) {
            val next = lines.drop(variant + 1).firstOrNull { !it.startsWith("#") } ?: return null
            return hlsDuration(resolve(url, next), client, headers, depth + 1)
        }
        // Without an end marker it is a live stream (or still being written): no duration.
        if (lines.none { it.startsWith("#EXT-X-ENDLIST") }) return null
        var seconds = 0.0
        for (line in lines) {
            if (!line.startsWith("#EXTINF:")) continue
            seconds += line.removePrefix("#EXTINF:").substringBefore(',').trim().toDoubleOrNull() ?: return null
        }
        return (seconds * 1000).toLong().takeIf { it > 0 }
    }

    private fun resolve(base: String, relative: String): String = runCatching { URI(base).resolve(relative).toString() }.getOrDefault(relative)

    private fun fetchText(client: OkHttpClient, url: String, headers: Map<String, String>): String? {
        val request = Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body.source()
            source.request(MAX_PLAYLIST.toLong())
            if (source.buffer.size >= MAX_PLAYLIST) null else source.readUtf8()
        }
    }

    // --- bytes ------------------------------------------------------------------------------------------

    private fun u32(b: ByteArray, pos: Int): Long =
        ((b[pos].toLong() and 0xFF) shl 24) or ((b[pos + 1].toLong() and 0xFF) shl 16) or ((b[pos + 2].toLong() and 0xFF) shl 8) or (b[pos + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, pos: Int): Long = (u32(b, pos) shl 32) or u32(b, pos + 4)
}

/**
 * A file served over HTTP, read by Range requests. A server that ignores Range sends the file from its
 * start whatever is asked: its first [PREFIX] bytes are then kept and later reads are served from them
 * (a header at the front is still found), and the rest of the file is never downloaded.
 */
internal class HttpRanges(private val client: OkHttpClient, private val url: String, private val headers: Map<String, String>) : Ranges {
    override var size: Long? = null
        private set

    /** The start of the file, once a server has shown it ignores Range. */
    private var prefix: ByteArray? = null

    override fun read(offset: Long, length: Int): ByteArray? {
        prefix?.let { kept ->
            if (offset >= kept.size) return if (size == kept.size.toLong()) ByteArray(0) else null
            val end = offset + length
            return if (end <= kept.size || size == kept.size.toLong()) kept.copyOfRange(offset.toInt(), minOf(end, kept.size.toLong()).toInt()) else null
        }
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
            header("Range", "bytes=$offset-${offset + length - 1}")
        }.build()
        return client.newCall(request).execute().use { response ->
            when (response.code) {
                206 -> {
                    response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { size = it }
                    readAtMost(response, length)
                }
                200 -> {
                    response.header("Content-Length")?.toLongOrNull()?.let { size = it }
                    if (offset != 0L) return null
                    val kept = readAtMost(response, maxOf(length, PREFIX))
                    prefix = kept
                    // The whole file fits: its size is then known, and past it there is nothing.
                    if (kept.size < maxOf(length, PREFIX)) size = kept.size.toLong()
                    kept.copyOfRange(0, minOf(length, kept.size))
                }
                416 -> ByteArray(0)
                else -> null
            }
        }
    }

    private fun readAtMost(response: okhttp3.Response, length: Int): ByteArray {
        val source = response.body.source()
        source.request(length.toLong())
        return source.readByteArray(minOf(source.buffer.size, length.toLong()))
    }

    private companion object {
        const val PREFIX = 1024 * 1024
    }
}
