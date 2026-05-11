package com.nearlink.messenger.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.database.NearLinkDatabase
import com.nearlink.messenger.core.model.MessageStatus
import com.nearlink.messenger.core.model.MessageType
import com.nearlink.messenger.data.local.entity.ConversationEntity
import com.nearlink.messenger.data.local.entity.MessageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {

    private lateinit var db: NearLinkDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NearLinkDatabase::class.java
        ).allowMainThreadQueries().build()
        runBlocking {
            db.conversationDao().upsert(ConversationEntity(convId = "peerA", peerDeviceId = "peerA", title = "A"))
        }
    }

    @After fun tearDown() = db.close()

    private fun msg(id: String) = MessageEntity(
        id = id,
        convId = "peerA",
        senderDeviceId = "self",
        recipientDeviceId = "peerA",
        type = MessageType.TEXT,
        status = MessageStatus.DELIVERED,
        isOutgoing = false,
        createdAtMs = System.currentTimeMillis(),
        updatedAtMs = System.currentTimeMillis(),
        text = "hi",
    )

    @Test fun insertWithDedup_ignoresDuplicates() = runBlocking {
        val first = db.messageDao().insertWithDedup(msg("01H_DUP"))
        val second = db.messageDao().insertWithDedup(msg("01H_DUP"))
        assertThat(first).isTrue()
        assertThat(second).isFalse()
    }

    @Test fun update_status_persists() = runBlocking {
        db.messageDao().insertWithDedup(msg("01H_UP"))
        db.messageDao().updateStatus("01H_UP", MessageStatus.READ, System.currentTimeMillis(), readAt = 1L)
        val row = db.messageDao().getById("01H_UP")
        assertThat(row?.status).isEqualTo(MessageStatus.READ)
        assertThat(row?.readAtMs).isEqualTo(1L)
    }
}
