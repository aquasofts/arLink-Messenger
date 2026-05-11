package com.nearlink.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nearlink.messenger.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conv: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, last_message_ts DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conv_id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE conv_id = :id LIMIT 1")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE peer_device_id = :peer LIMIT 1")
    suspend fun getByPeer(peer: String): ConversationEntity?

    @Query(
        """
        UPDATE conversations
        SET last_message_id = :msgId,
            last_message_preview = :preview,
            last_message_ts = :ts,
            unread_count = unread_count + :unreadDelta
        WHERE conv_id = :convId
        """
    )
    suspend fun updateLast(convId: String, msgId: String, preview: String?, ts: Long, unreadDelta: Int)

    @Query("UPDATE conversations SET unread_count = 0 WHERE conv_id = :convId")
    suspend fun clearUnread(convId: String)

    @Query("UPDATE conversations SET pinned = :pinned WHERE conv_id = :convId")
    suspend fun setPinned(convId: String, pinned: Boolean)

    @Query("DELETE FROM conversations WHERE conv_id = :convId")
    suspend fun delete(convId: String)
}
