package com.weslley.wesdownloader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadEntity?

    @Query("SELECT COUNT(*) > 0 FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PROCESSING')")
    suspend fun hasActive(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadEntity)

    @Update
    suspend fun update(item: DownloadEntity)

    @Delete
    suspend fun delete(item: DownloadEntity)

    @Query("UPDATE downloads SET status = 'INTERRUPTED', stage = 'Download interrompido', updatedAt = :now WHERE status IN ('QUEUED', 'DOWNLOADING', 'PROCESSING')")
    suspend fun markActiveInterrupted(now: Long)
}

