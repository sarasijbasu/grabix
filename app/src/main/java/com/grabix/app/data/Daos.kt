package com.grabix.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(item: DownloadEntity): Long

    @Insert
    suspend fun insertAll(items: List<DownloadEntity>): List<Long>

    @Update
    suspend fun update(item: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status NOT IN ('DONE','CANCELED') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'DONE' AND inVault = 0 ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'DONE' AND inVault = 1 ORDER BY completedAt DESC")
    fun observeVault(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','WAITING_WIFI') ORDER BY createdAt ASC")
    suspend fun pending(): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','WAITING_WIFI','RUNNING')")
    suspend fun countUnfinished(): Int

    @Query("UPDATE downloads SET progress = :progress, etaSec = :eta, speed = :speed WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, eta: Long, speed: String?)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?)

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'RUNNING'")
    suspend fun requeueInterrupted()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('FAILED','CANCELED')")
    suspend fun clearFailed()

    @Query("SELECT * FROM downloads WHERE status = 'DONE' AND filePath = :path LIMIT 1")
    suspend fun findByPath(path: String): DownloadEntity?

    @Query("SELECT url FROM downloads WHERE filePath = :path ORDER BY completedAt DESC LIMIT 1")
    suspend fun sourceForPath(path: String): String?
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(p: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    suspend fun clearItems(id: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    suspend fun allPlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position ASC")
    fun observeItems(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position ASC")
    suspend fun items(id: Long): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
}

@Dao
interface HistoryDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC LIMIT 300")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("UPDATE play_history SET positionMs = :pos, durationMs = :dur WHERE uri = :uri")
    suspend fun updatePosition(uri: String, pos: Long, dur: Long)

    @Query("DELETE FROM play_history WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}
