package com.nearlink.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nearlink.messenger.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE blocked = 0 ORDER BY nickname COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE device_id = :deviceId LIMIT 1")
    suspend fun getById(deviceId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE device_id = :deviceId LIMIT 1")
    fun observeById(deviceId: String): Flow<ContactEntity?>

    @Query("UPDATE contacts SET trust_state = :state, updated_at_ms = :now WHERE device_id = :deviceId")
    suspend fun setTrustState(deviceId: String, state: String, now: Long)

    @Query("UPDATE contacts SET last_seen_ms = :ts WHERE device_id = :deviceId")
    suspend fun setLastSeen(deviceId: String, ts: Long)

    @Query("UPDATE contacts SET blocked = :blocked WHERE device_id = :deviceId")
    suspend fun setBlocked(deviceId: String, blocked: Boolean)

    @Query("DELETE FROM contacts WHERE device_id = :deviceId")
    suspend fun delete(deviceId: String)
}
