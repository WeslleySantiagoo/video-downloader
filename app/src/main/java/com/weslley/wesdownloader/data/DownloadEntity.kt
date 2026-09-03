package com.weslley.wesdownloader.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.weslley.wesdownloader.domain.DownloadStatus
import com.weslley.wesdownloader.domain.MediaMode

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int,
    val mode: MediaMode,
    val qualityId: String,
    val formatId: String,
    val qualityLabel: String,
    val container: String,
    val estimatedBytes: Long?,
    val status: DownloadStatus,
    val progress: Int,
    val stage: String,
    val outputUri: String?,
    val fileName: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

