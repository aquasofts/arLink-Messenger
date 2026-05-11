package com.nearlink.messenger.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nearlink.messenger.data.local.dao.ContactDao
import com.nearlink.messenger.data.local.dao.ConversationDao
import com.nearlink.messenger.data.local.dao.KeyDao
import com.nearlink.messenger.data.local.dao.MessageDao
import com.nearlink.messenger.data.local.dao.OutboxDao
import com.nearlink.messenger.data.local.entity.ContactEntity
import com.nearlink.messenger.data.local.entity.ConversationEntity
import com.nearlink.messenger.data.local.entity.KeyEntity
import com.nearlink.messenger.data.local.entity.MessageEntity
import com.nearlink.messenger.data.local.entity.OutboxEntity

@Database(
    entities = [
        ContactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        KeyEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class NearLinkDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun keyDao(): KeyDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val NAME = "nearlink.db"
    }
}
