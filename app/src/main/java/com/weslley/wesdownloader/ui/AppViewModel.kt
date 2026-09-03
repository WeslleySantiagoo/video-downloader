package com.weslley.wesdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weslley.wesdownloader.WesDownloaderApp
import com.weslley.wesdownloader.data.DownloadEntity
import com.weslley.wesdownloader.domain.AppError
import com.weslley.wesdownloader.domain.DownloadStatus
import com.weslley.wesdownloader.domain.MediaInspection
import com.weslley.wesdownloader.domain.MediaMode
import com.weslley.wesdownloader.domain.QualityOption
import com.weslley.wesdownloader.download.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as WesDownloaderApp).container

    private val _url = MutableStateFlow("")
    val url = _url.asStateFlow()
    private val _inspection = MutableStateFlow<MediaInspection?>(null)
    val inspection = _inspection.asStateFlow()
    private val _mode = MutableStateFlow(MediaMode.VIDEO)
    val mode = _mode.asStateFlow()
    private val _selectedQuality = MutableStateFlow<QualityOption?>(null)
    val selectedQuality = _selectedQuality.asStateFlow()
    private val _isInspecting = MutableStateFlow(false)
    val isInspecting = _isInspecting.asStateFlow()
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    val downloads: StateFlow<List<DownloadEntity>> = container.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setUrl(value: String) {
        _url.value = extractUrl(value)
        _message.value = null
    }

    fun setMode(value: MediaMode) {
        _mode.value = value
        val media = _inspection.value ?: return
        _selectedQuality.value = if (value == MediaMode.VIDEO) media.videoOptions.lastOrNull() else media.audioOptions.firstOrNull()
    }

    fun selectQuality(value: QualityOption) {
        _selectedQuality.value = value
    }

    fun inspect() {
        if (_url.value.isBlank() || _isInspecting.value) return
        viewModelScope.launch {
            _isInspecting.value = true
            _message.value = null
            try {
                val result = container.extractor.inspect(_url.value)
                _inspection.value = result
                _mode.value = MediaMode.VIDEO
                _selectedQuality.value = result.videoOptions.lastOrNull()
            } catch (error: Exception) {
                _inspection.value = null
                _message.value = error.message ?: "Nao foi possivel analisar o link."
            } finally {
                _isInspecting.value = false
            }
        }
    }

    fun startDownload() {
        val media = _inspection.value ?: return
        val quality = _selectedQuality.value ?: return
        viewModelScope.launch {
            try {
                if (container.repository.hasActive()) throw AppError.Busy()
                if (!container.storage.hasSpaceFor(quality.estimatedBytes)) throw AppError.NoSpace()
                val now = System.currentTimeMillis()
                val item = DownloadEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = media.mediaId,
                    sourceUrl = media.sourceUrl,
                    title = media.title,
                    thumbnailUrl = media.thumbnailUrl,
                    durationSeconds = media.durationSeconds,
                    mode = _mode.value,
                    qualityId = quality.id,
                    formatId = quality.formatId,
                    qualityLabel = quality.label,
                    container = quality.container,
                    estimatedBytes = quality.estimatedBytes,
                    status = DownloadStatus.QUEUED,
                    progress = 0,
                    stage = "Na fila",
                    outputUri = null,
                    fileName = null,
                    errorMessage = null,
                    createdAt = now,
                    updatedAt = now,
                )
                container.repository.insert(item)
                DownloadService.start(getApplication(), item.id)
                clearForm()
            } catch (error: Exception) {
                _message.value = error.message ?: "Nao foi possivel iniciar o download."
            }
        }
    }

    fun cancel(item: DownloadEntity) {
        DownloadService.cancel(getApplication(), item.id)
    }

    fun resume(item: DownloadEntity) {
        viewModelScope.launch {
            if (container.repository.hasActive()) {
                _message.value = AppError.Busy().message
                return@launch
            }
            container.repository.update(item.copy(
                status = DownloadStatus.QUEUED,
                progress = 0,
                stage = "Retomando",
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ))
            DownloadService.start(getApplication(), item.id)
        }
    }

    fun remove(item: DownloadEntity) {
        viewModelScope.launch {
            container.storage.deleteTemporary(item.id)
            container.repository.delete(item.id)
        }
    }

    fun updateEngine() {
        if (_isUpdating.value) return
        viewModelScope.launch {
            _isUpdating.value = true
            _message.value = runCatching { container.extractor.updateEngine() }
                .getOrElse { "Nao foi possivel atualizar o mecanismo." }
            _isUpdating.value = false
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun reset() {
        clearForm()
        _url.value = ""
    }

    private fun clearForm() {
        _inspection.value = null
        _selectedQuality.value = null
        _mode.value = MediaMode.VIDEO
        _url.value = ""
        _message.value = null
    }

    private fun extractUrl(text: String): String = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        .find(text.trim())?.value?.trimEnd('.', ',', ')', ']') ?: text.trim()

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(application) as T
        }
    }
}

