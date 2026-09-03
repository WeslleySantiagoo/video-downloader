package com.weslley.wesdownloader.download

import com.weslley.wesdownloader.data.DownloadEntity
import com.weslley.wesdownloader.domain.DownloadProgress
import com.weslley.wesdownloader.domain.MediaInspection
import java.io.File

interface MediaExtractor {
    suspend fun initialize()
    suspend fun inspect(url: String): MediaInspection
    suspend fun download(item: DownloadEntity, directory: File, onProgress: suspend (DownloadProgress) -> Unit): File
    fun cancel(id: String): Boolean
    suspend fun updateEngine(): String
    fun engineVersion(): String?
}

