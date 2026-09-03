package com.weslley.wesdownloader.domain

data class RawFormat(
    val id: String,
    val height: Int,
    val extension: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val fileSize: Long?,
)

object FormatSelector {
    fun videoOptions(formats: List<RawFormat>): List<QualityOption> = formats
        .asSequence()
        .filter { it.id.isNotBlank() && it.height > 0 && it.videoCodec != null && it.videoCodec != "none" }
        .groupBy { it.height }
        .mapNotNull { (height, values) ->
            val best = values.sortedWith(
                compareByDescending<RawFormat> { it.extension.equals("mp4", ignoreCase = true) }
                    .thenByDescending { it.audioCodec != null && it.audioCodec != "none" }
                    .thenByDescending { it.fileSize ?: 0L },
            ).firstOrNull() ?: return@mapNotNull null
            QualityOption(
                id = "video-$height-${best.id}",
                formatId = best.id,
                label = "${height}p",
                height = height,
                container = if (best.extension.equals("mp4", true)) "mp4" else "webm",
                estimatedBytes = best.fileSize,
            )
        }
        .sortedBy { it.height }

    fun videoDownloadSelector(option: QualityOption): String {
        val audio = if (option.container == "mp4") "bestaudio[ext=m4a]/bestaudio" else "bestaudio[ext=webm]/bestaudio"
        return "${option.formatId}+$audio/best[height=${option.height}]"
    }
}

