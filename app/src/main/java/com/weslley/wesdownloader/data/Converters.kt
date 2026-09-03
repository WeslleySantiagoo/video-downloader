package com.weslley.wesdownloader.data

import androidx.room.TypeConverter
import com.weslley.wesdownloader.domain.DownloadStatus
import com.weslley.wesdownloader.domain.MediaMode

class Converters {
    @TypeConverter fun mediaMode(value: String): MediaMode = MediaMode.valueOf(value)
    @TypeConverter fun mediaMode(value: MediaMode): String = value.name
    @TypeConverter fun downloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
    @TypeConverter fun downloadStatus(value: DownloadStatus): String = value.name
}

