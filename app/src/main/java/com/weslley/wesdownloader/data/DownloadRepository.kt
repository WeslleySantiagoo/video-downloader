package com.weslley.wesdownloader.data

import com.weslley.wesdownloader.domain.DownloadStatus
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadEntity>>
    suspend fun get(id: String): DownloadEntity?
    suspend fun hasActive(): Boolean
    suspend fun insert(item: DownloadEntity)
    suspend fun update(item: DownloadEntity)
    suspend fun updateProgress(id: String, status: DownloadStatus, progress: Int, stage: String)
    suspend fun finish(id: String, outputUri: String, fileName: String)
    suspend fun fail(id: String, status: DownloadStatus, message: String)
    suspend fun delete(id: String)
    suspend fun markActiveInterrupted()
}

class RoomDownloadRepository(private val dao: DownloadDao) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    override suspend fun get(id: String): DownloadEntity? = dao.get(id)
    override suspend fun hasActive(): Boolean = dao.hasActive()
    override suspend fun insert(item: DownloadEntity) = dao.insert(item)
    override suspend fun update(item: DownloadEntity) = dao.update(item)

    override suspend fun updateProgress(id: String, status: DownloadStatus, progress: Int, stage: String) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(status = status, progress = progress.coerceIn(0, 100), stage = stage, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun finish(id: String, outputUri: String, fileName: String) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(
            status = DownloadStatus.COMPLETED,
            progress = 100,
            stage = "Pronto",
            outputUri = outputUri,
            fileName = fileName,
            errorMessage = null,
            updatedAt = System.currentTimeMillis(),
        ))
    }

    override suspend fun fail(id: String, status: DownloadStatus, message: String) {
        val current = dao.get(id) ?: return
        dao.update(current.copy(status = status, stage = message, errorMessage = message, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(id: String) {
        dao.get(id)?.let { dao.delete(it) }
    }

    override suspend fun markActiveInterrupted() = dao.markActiveInterrupted(System.currentTimeMillis())
}
