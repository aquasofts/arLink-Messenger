package com.nearlink.messenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nearlink.messenger.data.local.entity.KeyEntity

@Dao
interface KeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: KeyEntity)

    @Query("SELECT * FROM keys WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): KeyEntity?

    @Query("DELETE FROM keys WHERE id = :id")
    suspend fun delete(id: String)
}
