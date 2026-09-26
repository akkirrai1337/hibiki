package org.akkirrai.hibiki.feature.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.akkirrai.hibiki.core.log.AppLogger

private const val TS_PACKET = 188
private const val TS_SYNC = 0x47.toByte()
private const val REPAIR_TAG = "HibikiTsRepair"

/**
 * Some streams (AnimePahe's, for one) carry a damaged PAT in every segment but the first: the PID of the PMT is
 * overwritten with a wrong value while the PMT itself sits on its real PID and the table's CRC still belongs to the
 * original bytes. A player that plays from the start never notices, because the first segment is intact and the
 * extractor keeps what it learned from it. One that starts anywhere else (a resume, a seek) asks for a PMT that does
 * not exist, never finds the tracks and never prepares.
 *
 * This wrapper looks at the first packets of an MPEG-TS body, finds the real PMT (the first packet after the PAT that
 * starts a table with id 2) and, if the PAT points somewhere else, rewrites the PAT's PMT PID and its CRC. Anything
 * that is not a plain MPEG-TS body, or whose PAT is already right, passes through untouched.
 */
@UnstableApi
internal class TsPatRepairDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = TsPatRepairDataSource(upstream.createDataSource())
}

@UnstableApi
private class TsPatRepairDataSource(private val upstream: DataSource) : DataSource {
    private var head: ByteArray? = null
    private var headPosition = 0
    private var inspect = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        head = null
        headPosition = 0
        // Only a body read from its start can have its PAT at the front.
        inspect = dataSpec.position == 0L
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (inspect) {
            inspect = false
            head = readHead()
        }
        val ready = head
        if (ready != null && headPosition < ready.size) {
            val count = minOf(length, ready.size - headPosition)
            System.arraycopy(ready, headPosition, buffer, offset, count)
            headPosition += count
            return count
        }
        return upstream.read(buffer, offset, length)
    }

    /** The first packets of the body, repaired if needed; null when the source ended before anything was read. */
    private fun readHead(): ByteArray? {
        val bytes = ByteArray(HEAD_PACKETS * TS_PACKET)
        var filled = 0
        while (filled < bytes.size) {
            val read = upstream.read(bytes, filled, bytes.size - filled)
            if (read <= 0) break
            filled += read
        }
        if (filled == 0) return null
        val head = bytes.copyOf(filled)
        if (head[0] == TS_SYNC) repairPat(head)
        return head
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        head = null
        upstream.close()
    }

    private fun repairPat(head: ByteArray) {
        val packets = head.size / TS_PACKET
        var patPacket = -1
        var pmtPid = -1
        for (n in 0 until packets) {
            val start = n * TS_PACKET
            if (head[start] != TS_SYNC) return
            val pid = ((head[start + 1].toInt() and 0x1f) shl 8) or (head[start + 2].toInt() and 0xff)
            val payloadStart = (head[start + 1].toInt() and 0x40) != 0
            val payload = payloadOffset(head, start) ?: continue
            if (!payloadStart) continue
            if (pid == 0) {
                if (patPacket < 0) patPacket = n
            } else if (patPacket >= 0 && pmtPid < 0) {
                val pointer = head[payload].toInt() and 0xff
                val tableAt = payload + 1 + pointer
                if (tableAt < start + TS_PACKET && (head[tableAt].toInt() and 0xff) == 0x02) pmtPid = pid
            }
        }
        if (patPacket < 0 || pmtPid < 0) return
        val start = patPacket * TS_PACKET
        val payload = payloadOffset(head, start) ?: return
        val tableAt = payload + 1 + (head[payload].toInt() and 0xff)
        if (tableAt + 3 > start + TS_PACKET || head[tableAt].toInt() != 0) return
        val sectionLength = ((head[tableAt + 1].toInt() and 0x0f) shl 8) or (head[tableAt + 2].toInt() and 0xff)
        val tableEnd = tableAt + 3 + sectionLength
        if (tableEnd > start + TS_PACKET || sectionLength < 9) return
        // Entries follow the 5 bytes after the length field, 4 bytes each, up to the 4-byte CRC.
        var changed = false
        var entry = tableAt + 8
        while (entry + 4 <= tableEnd - 4) {
            val program = ((head[entry].toInt() and 0xff) shl 8) or (head[entry + 1].toInt() and 0xff)
            val current = ((head[entry + 2].toInt() and 0x1f) shl 8) or (head[entry + 3].toInt() and 0xff)
            if (program != 0 && current != pmtPid) {
                head[entry + 2] = ((head[entry + 2].toInt() and 0xe0) or (pmtPid shr 8)).toByte()
                head[entry + 3] = (pmtPid and 0xff).toByte()
                changed = true
            }
            entry += 4
        }
        if (!changed) return
        val crc = mpegCrc32(head, tableAt, tableEnd - 4)
        head[tableEnd - 4] = (crc ushr 24).toByte()
        head[tableEnd - 3] = (crc ushr 16).toByte()
        head[tableEnd - 2] = (crc ushr 8).toByte()
        head[tableEnd - 1] = crc.toByte()
        AppLogger.d(REPAIR_TAG, "PAT repaired: PMT PID set to $pmtPid")
    }

    /** Where the payload of the packet starting at [start] begins, or null if it has none. */
    private fun payloadOffset(head: ByteArray, start: Int): Int? {
        val control = (head[start + 3].toInt() shr 4) and 3
        if (control and 1 == 0) return null
        var offset = start + 4
        if (control and 2 != 0) offset += 1 + (head[offset].toInt() and 0xff)
        return offset.takeIf { it < start + TS_PACKET }
    }

    private fun mpegCrc32(data: ByteArray, from: Int, to: Int): Int {
        var crc = -1
        for (n in from until to) {
            crc = crc xor ((data[n].toInt() and 0xff) shl 24)
            repeat(8) {
                crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1
            }
        }
        return crc
    }

    private companion object {
        /** PAT, PMT and the first media packets: enough to find the real PMT. */
        const val HEAD_PACKETS = 4
    }
}
