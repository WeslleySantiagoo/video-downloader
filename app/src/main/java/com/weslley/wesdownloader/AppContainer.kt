package com.weslley.wesdownloader

import android.content.Context
import androidx.room.Room
import com.weslley.wesdownloader.data.AppDatabase
import com.weslley.wesdownloader.data.DownloadRepository
import com.weslley.wesdownloader.data.RoomDownloadRepository
import com.weslley.wesdownloader.download.DeviceStorage
import com.weslley.wesdownloader.download.MediaExtractor
import com.weslley.wesdownloader.download.YoutubeDlMediaExtractor

class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(context, AppDatabase::class.java, "wesdownloader.db").build()
    val repository: DownloadRepository = RoomDownloadRepository(database.downloads())
    val extractor: MediaExtractor = YoutubeDlMediaExtractor(context)
    val storage = DeviceStorage(context)
}

