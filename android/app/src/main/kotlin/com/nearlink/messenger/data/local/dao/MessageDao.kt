package com.nearlink.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nearlink.messenger.core.model.MessageStatus
import com.nearlink.messenger.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /**
     * 插入或忽略：客户端 msg_id 是去重唯一键。
     * 同一条消息可能被蓝牙和服务器同时投递，只取第一条，后到的丢弃。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE conv_id = :convId
        ORDER BY created_at_ms ASC, id ASC
        """
    )
    fun observeConv(convId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conv_id = :convId
        ORDER BY created_at_ms DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun pageDesc(convId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query(
        """
        UPDATE messages
        SET status = :status, updated_at_ms = :now,
            delivered_at_ms = COALESCE(:deliveredAt, delivered_at_ms),
            read_at_ms = COALESCE(:readAt, read_at_ms)
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: MessageStatus,
        now: Long,
        deliveredAt: Long? = null,
        readAt: Long? = null,
    )

    @Query(
        """
        UPDATE messages
        SET read_at_ms = :readAt, status = :status, updated_at_ms = :readAt
        WHERE conv_id = :convId AND is_outgoing = 1 AND read_at_ms IS NULL AND created_at_ms <= :upToTs
        """
    )
    suspend fun markReadUpTo(convId: String, upToTs: Long, readAt: Long, status: MessageStatus = MessageStatus.READ)

    @Query("UPDATE messages SET revoked = 1, text = NULL, updated_at_ms = :now WHERE id = :id")
    suspend fun revoke(id: String, now: Long)

    @Query("UPDATE messages SET text = :newText, edited = 1, updated_at_ms = :now WHERE id = :id")
    suspend fun edit(id: String, newText: String, now: Long)

    @Query("UPDATE messages SET reactions_json = :json, updated_at_ms = :now WHERE id = :id")
    suspend fun setReactions(id: String, json: String?, now: Long)

    @Transaction
    suspend fun insertWithDedup(message: MessageEntity): Boolean {
        val rowId = insertIfAbsent(message)
        return rowId != -1L
    }

    @Query("DELETE FROM messages WHERE conv_id = :convId")
    suspend fun deleteConv(convId: String)
}
