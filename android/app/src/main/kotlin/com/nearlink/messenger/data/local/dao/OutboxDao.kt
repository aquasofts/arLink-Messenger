package com.nearlink.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nearlink.messenger.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE next_attempt_at_ms <= :now ORDER BY next_attempt_at_ms ASC LIMIT :limit")
    suspend fun pickDue(now: Long, limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("UPDATE outbox SET attempts = attempts + 1, next_attempt_at_ms = :nextAt WHERE client_msg_id = :id")
    suspend fun reschedule(id: String, nextAt: Long)

    @Query("DELETE FROM outbox WHERE client_msg_id = :id")
    suspend fun delete(id: String)
}
