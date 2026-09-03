package com.weslley.wesdownloader.domain

enum class MediaMode { VIDEO, AUDIO }

data class QualityOption(
    val id: String,
    val formatId: String,
    val label: String,
    val height: Int?,
    val container: String,
    val estimatedBytes: Long?,
)

data class MediaInspection(
    val mediaId: String,
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val videoOptions: List<QualityOption>,
    val audioOptions: List<QualityOption> = listOf(
        QualityOption("audio-best", "bestaudio", "Melhor qualidade", null, "mp3", null),
    ),
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

data class DownloadProgress(
    val percent: Int,
    val etaSeconds: Long,
    val stage: String,
)

sealed class AppError(message: String) : Exception(message) {
    class InvalidUrl(message: String = "Cole um link valido de um video do YouTube.") : AppError(message)
    class Playlist : AppError("Playlists ainda nao sao aceitas.")
    class Live : AppError("Transmissoes ao vivo nao sao aceitas.")
    class Unavailable : AppError("Nao foi possivel acessar esse video.")
    class NoFormats : AppError("Nenhuma qualidade compativel foi encontrada.")
    class NoSpace : AppError("Nao ha espaco livre suficiente no aparelho.")
    class Busy : AppError("Aguarde o download atual terminar ou cancele-o.")
}

