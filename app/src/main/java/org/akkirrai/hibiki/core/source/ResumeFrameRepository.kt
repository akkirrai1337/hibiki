package org.akkirrai.hibiki.core.source

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest

class ResumeFrameRepository(context: Context) {
    private val framesDirectory = File(context.applicationContext.filesDir, FRAMES_DIRECTORY).apply {
        mkdirs()
    }

    fun getFrame(titleId: String): File? {
        return YummyIdMigration.compatibleTitleIds(titleId)
            .firstNotNullOfOrNull { candidate ->
                frameFile(candidate).takeIf { it.isFile && it.length() > 0L }
            }
    }

    fun saveFrame(titleId: String, bitmap: Bitmap): File? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val target = frameFile(YummyIdMigration.normalizeTitleId(titleId))
        val temporary = File(target.parentFile, "${target.name}.tmp")
        val scaled = bitmap.scaleToMaxWidth(MAX_FRAME_WIDTH)
        return runCatching {
            temporary.outputStream().buffered().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            }
            check(!target.exists() || target.delete())
            check(temporary.renameTo(target))
            target
        }.getOrNull().also {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            if (it == null) temporary.delete()
        }
    }

    private fun frameFile(titleId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(titleId.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(framesDirectory, "$digest.jpg")
    }

    private fun Bitmap.scaleToMaxWidth(maxWidth: Int): Bitmap {
        if (width <= maxWidth) return this
        val targetHeight = (height.toFloat() * maxWidth / width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, maxWidth, targetHeight, true)
    }

    private companion object {
        const val FRAMES_DIRECTORY = "resume_frames"
        const val MAX_FRAME_WIDTH = 1280
        const val JPEG_QUALITY = 86
    }
}
