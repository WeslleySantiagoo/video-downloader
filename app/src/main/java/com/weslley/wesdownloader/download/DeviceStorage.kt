package com.weslley.wesdownloader.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.weslley.wesdownloader.domain.MediaMode
import java.io.File
import java.io.FileInputStream

class DeviceStorage(private val context: Context) {
    fun hasSpaceFor(estimatedBytes: Long?): Boolean {
        if (estimatedBytes == null || estimatedBytes <= 0) return true
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        return available > (estimatedBytes * 1.2).toLong()
    }

    fun tempDirectory(id: String): File = File(context.filesDir, "downloads/$id")

    fun deleteTemporary(id: String) {
        tempDirectory(id).deleteRecursively()
    }

    fun publish(source: File, title: String, mode: MediaMode): Pair<Uri, String> {
        val extension = source.extension.ifBlank { if (mode == MediaMode.AUDIO) "mp3" else "mp4" }
        val fileName = "${FileNames.sanitize(title)}.$extension"
        val mime = if (mode == MediaMode.AUDIO) {
            if (extension == "m4a") "audio/mp4" else "audio/mpeg"
        } else if (extension == "webm") {
            "video/webm"
        } else {
            "video/mp4"
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishWithMediaStore(source, fileName, mime, mode)
        } else {
            publishLegacy(source, fileName, mime)
        }
    }

    private fun publishWithMediaStore(source: File, fileName: String, mime: String, mode: MediaMode): Pair<Uri, String> {
        val collection = if (mode == MediaMode.AUDIO) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/WesDownloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Nao foi possivel criar o arquivo em Downloads.")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Nao foi possivel abrir o destino.")
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri to fileName
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(source: File, fileName: String, mime: String): Pair<Uri, String> {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WesDownloader")
        directory.mkdirs()
        val target = uniqueFile(directory, fileName)
        source.copyTo(target, overwrite = false)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        return uri to target.name
    }

    private fun uniqueFile(directory: File, requested: String): File {
        val direct = File(directory, requested)
        if (!direct.exists()) return direct
        val base = direct.nameWithoutExtension
        val extension = direct.extension
        var counter = 2
        while (true) {
            val candidate = File(directory, "$base ($counter).$extension")
            if (!candidate.exists()) return candidate
            counter++
        }
    }
}
