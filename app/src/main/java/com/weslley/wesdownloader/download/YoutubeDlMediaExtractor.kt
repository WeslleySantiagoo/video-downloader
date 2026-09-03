package com.weslley.wesdownloader.download

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.weslley.wesdownloader.data.DownloadEntity
import com.weslley.wesdownloader.domain.AppError
import com.weslley.wesdownloader.domain.DownloadProgress
import com.weslley.wesdownloader.domain.FormatSelector
import com.weslley.wesdownloader.domain.MediaInspection
import com.weslley.wesdownloader.domain.MediaMode
import com.weslley.wesdownloader.domain.QualityOption
import com.weslley.wesdownloader.domain.RawFormat
import com.weslley.wesdownloader.domain.YouTubeUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class YoutubeDlMediaExtractor(context: Context) : MediaExtractor {
    private val appContext = context.applicationContext
    private val initialization = Mutex()
    @Volatile private var initialized = false

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        initialization.withLock {
            if (initialized) return@withLock
            YoutubeDL.init(appContext)
            FFmpeg.init(appContext)
            Aria2c.init(appContext)
            initialized = true
        }
    }

    override suspend fun inspect(url: String): MediaInspection = withContext(Dispatchers.IO) {
        initialize()
        val normalized = YouTubeUrlValidator.normalize(url)
        val request = YoutubeDLRequest(normalized)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--extractor-args", "youtube:player_client=android")
            .addOption("--socket-timeout", 20)

        val root = try {
            val response = YoutubeDL.execute(request)
            YoutubeDL.objectMapper.readTree(response.out)
        } catch (error: Exception) {
            throw AppError.Unavailable()
        }

        if (root.path("_type").asText() == "playlist" || root.path("entries").isArray) throw AppError.Playlist()
        if (root.path("is_live").asBoolean(false) || root.path("live_status").asText() in setOf("is_live", "is_upcoming")) {
            throw AppError.Live()
        }

        val formats = root.path("formats").mapNotNull { node ->
            val id = node.path("format_id").asText()
            val height = node.path("height").asInt(0)
            if (id.isBlank() || height <= 0) return@mapNotNull null
            RawFormat(
                id = id,
                height = height,
                extension = node.path("ext").asText("webm"),
                videoCodec = node.path("vcodec").asText(null),
                audioCodec = node.path("acodec").asText(null),
                fileSize = node.path("filesize").takeIf { it.isNumber }?.asLong()
                    ?: node.path("filesize_approx").takeIf { it.isNumber }?.asLong(),
            )
        }
        val videoOptions = FormatSelector.videoOptions(formats)
        if (videoOptions.isEmpty()) throw AppError.NoFormats()
        val bestAudioSize = root.path("formats")
            .filter { it.path("acodec").asText("none") != "none" }
            .mapNotNull { it.path("filesize").takeIf { size -> size.isNumber }?.asLong() }
            .maxOrNull()

        MediaInspection(
            mediaId = root.path("id").asText(),
            sourceUrl = normalized,
            title = root.path("title").asText("Video sem titulo").take(200),
            thumbnailUrl = root.path("thumbnail").asText().ifBlank { null },
            durationSeconds = root.path("duration").asInt(0),
            videoOptions = videoOptions.map { option ->
                option.copy(estimatedBytes = option.estimatedBytes?.let { it + (bestAudioSize ?: 0L) })
            },
            audioOptions = listOf(QualityOption("audio-best", "bestaudio", "Melhor qualidade", null, "mp3", bestAudioSize)),
        )
    }

    override suspend fun download(
        item: DownloadEntity,
        directory: File,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        initialize()
        directory.mkdirs()
        val request = baseRequest(item, directory)
        try {
            executeDownload(request, item.id, onProgress)
        } catch (error: YoutubeDLException) {
            if (item.mode != MediaMode.AUDIO) throw error
            directory.listFiles()?.filterNot { it.name.endsWith(".part") }?.forEach { it.delete() }
            val fallback = YoutubeDLRequest(item.sourceUrl)
                .addOption("--no-playlist")
                .addOption("--newline")
                .addOption("--continue")
                .addOption("--no-overwrites")
                .addOption("--downloader", "libaria2c.so")
                .addOption("--downloader-args", "aria2c:-x 4 -k 1M")
                .addOption("--extractor-args", "youtube:player_client=android")
                .addOption("-f", "bestaudio[ext=m4a]")
                .addOption("-o", File(directory, "%(title).100B [%(id)s].%(ext)s").absolutePath)
            executeDownload(fallback, item.id, onProgress)
        }
        findOutput(directory)
    }

    private fun baseRequest(item: DownloadEntity, directory: File): YoutubeDLRequest {
        val request = YoutubeDLRequest(item.sourceUrl)
            .addOption("--no-playlist")
            .addOption("--newline")
            .addOption("--continue")
            .addOption("--no-overwrites")
            .addOption("--downloader", "libaria2c.so")
            .addOption("--downloader-args", "aria2c:-x 4 -k 1M")
            .addOption("--extractor-args", "youtube:player_client=android")
            .addOption("--socket-timeout", 20)
            .addOption("--retries", 5)
            .addOption("-o", File(directory, "%(title).100B [%(id)s].%(ext)s").absolutePath)

        return if (item.mode == MediaMode.AUDIO) {
            request
                .addOption("-f", "bestaudio/best")
                .addOption("--extract-audio")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", "0")
        } else {
            val option = QualityOption(
                id = item.qualityId,
                formatId = item.formatId,
                label = item.qualityLabel,
                height = item.qualityLabel.removeSuffix("p").toIntOrNull(),
                container = item.container,
                estimatedBytes = item.estimatedBytes,
            )
            request
                .addOption("-f", FormatSelector.videoDownloadSelector(option))
                .addOption("--merge-output-format", item.container)
        }
    }

    private fun executeDownload(
        request: YoutubeDLRequest,
        processId: String,
        onProgress: suspend (DownloadProgress) -> Unit,
    ) {
        YoutubeDL.execute(request, processId) { progress, eta, line ->
            val stage = if (line.contains("Merger", true) || line.contains("ExtractAudio", true)) {
                "Processando arquivo"
            } else {
                "Baixando midia"
            }
            runBlocking { onProgress(DownloadProgress(progress.toInt().coerceIn(0, 95), eta, stage)) }
        }
    }

    private fun findOutput(directory: File): File = directory.listFiles()
        ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
        ?.maxByOrNull { it.lastModified() }
        ?: throw IllegalStateException("O arquivo final nao foi gerado.")

    override fun cancel(id: String): Boolean = YoutubeDL.destroyProcessById(id)

    override suspend fun updateEngine(): String = withContext(Dispatchers.IO) {
        initialize()
        when (YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)) {
            YoutubeDL.UpdateStatus.DONE -> "Mecanismo atualizado"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Mecanismo ja esta atualizado"
            null -> "Nao foi possivel verificar atualizacoes"
        }
    }

    override fun engineVersion(): String? = YoutubeDL.versionName(appContext)
}
