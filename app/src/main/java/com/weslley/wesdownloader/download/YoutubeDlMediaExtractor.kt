package com.weslley.wesdownloader.download

import android.content.Context
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
    private val operation = Mutex()
    @Volatile private var initialized = false
    @Volatile private var updateAttempted = false

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        initialization.withLock {
            if (initialized) return@withLock
            YoutubeDL.init(appContext)
            FFmpeg.init(appContext)
            initialized = true
        }
    }

    override suspend fun inspect(url: String): MediaInspection = withContext(Dispatchers.IO) {
        initialize()
        val normalized = YouTubeUrlValidator.normalize(url)
        operation.withLock {
            if (!updateAttempted) {
                updateAttempted = true
                runCatching { updateEngineWithoutLock() }
            }
            try {
                inspectOnce(normalized)
            } catch (error: AppError) {
                throw error
            } catch (error: Exception) {
                throw AppError.Unavailable()
            }
        }
    }

    private fun inspectOnce(normalized: String): MediaInspection {
        val request = YoutubeDLRequest(normalized)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--socket-timeout", 30)
            .addOption("--extractor-retries", 3)
            .addOption("--retry-sleep", "extractor:linear=1:3:1")

        val response = YoutubeDL.execute(request)
        val root = YoutubeDL.objectMapper.readTree(response.out)

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

        return MediaInspection(
            mediaId = root.path("id").asText(),
            sourceUrl = normalized,
            title = root.path("title").asText("Video sem titulo").take(200),
            thumbnailUrl = root.path("thumbnail").asText().ifBlank { null },
            durationSeconds = root.path("duration").asInt(0),
            videoOptions = videoOptions.map { option ->
                option.copy(estimatedBytes = option.estimatedBytes?.let {
                    if (option.hasAudio) it else it + (bestAudioSize ?: 0L)
                })
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
        operation.withLock {
            directory.mkdirs()
            val request = baseRequest(item, directory)
            try {
                executeDownload(request, item.id, onProgress)
            } catch (error: YoutubeDLException) {
                if (item.mode != MediaMode.AUDIO) throw error
                directory.listFiles()?.filterNot { it.name.endsWith(".part") }?.forEach { it.delete() }
                val fallback = reliableRequest(YoutubeDLRequest(item.sourceUrl), directory)
                    .addOption("-f", "bestaudio[ext=m4a]")
                executeDownload(fallback, item.id, onProgress)
            }
            findOutput(directory)
        }
    }

    private fun baseRequest(item: DownloadEntity, directory: File): YoutubeDLRequest {
        val request = reliableRequest(YoutubeDLRequest(item.sourceUrl), directory)

        return if (item.mode == MediaMode.AUDIO) {
            request
                .addOption("-f", "bestaudio/best")
                .addOption("--extract-audio")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", "0")
        } else {
            val height = item.qualityLabel.removeSuffix("p").toIntOrNull() ?: 2160
            val selector = FormatSelector.repairPersistedVideoSelector(item.formatId, height, item.container)
            request
                .addOption("-f", selector)
                .addOption("--merge-output-format", item.container)
        }
    }

    private fun reliableRequest(request: YoutubeDLRequest, directory: File): YoutubeDLRequest = request
        .addOption("--ignore-config")
        .addOption("--no-playlist")
        .addOption("--newline")
        .addOption("--continue")
        .addOption("--no-overwrites")
        .addOption("--socket-timeout", 30)
        .addOption("--retries", 10)
        .addOption("--fragment-retries", 10)
        .addOption("--file-access-retries", 3)
        .addOption("--extractor-retries", 3)
        .addOption("--retry-sleep", "http:linear=1:5:1")
        .addOption("--retry-sleep", "fragment:exp=1:10")
        .addOption("--throttled-rate", "100K")
        .addOption("-o", File(directory, "%(title).100B [%(id)s].%(ext)s").absolutePath)

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
        operation.withLock {
            updateAttempted = true
            updateEngineWithoutLock()
        }
    }

    private fun updateEngineWithoutLock(): String {
        return when (YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)) {
            YoutubeDL.UpdateStatus.DONE -> "Mecanismo atualizado"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Mecanismo ja esta atualizado"
            null -> "Nao foi possivel verificar atualizacoes"
        }
    }

    override fun engineVersion(): String? = YoutubeDL.versionName(appContext)
}
