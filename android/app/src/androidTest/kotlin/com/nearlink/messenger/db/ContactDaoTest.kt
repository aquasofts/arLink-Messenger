package com.nearlink.messenger.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.database.NearLinkDatabase
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactDaoTest {

    private lateinit var db: NearLinkDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NearLinkDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun contact(id: String, name: String, trust: TrustState = TrustState.UNVERIFIED) = ContactEntity(
        deviceId = id,
        nickname = name,
        pkIdentity = ByteArray(32) { 1 },
        pkX = ByteArray(32) { 2 },
        trustState = trust,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    @Test fun upsert_then_observe_includes_only_unblocked() = runBlocking {
        val dao = db.contactDao()
        dao.upsert(contact("a", "Alice"))
        dao.upsert(contact("b", "Bob"))
        dao.setBlocked("b", true)
        val list = dao.observeAll().first()
        assertThat(list.map { it.deviceId }).containsExactly("a")
    }

    @Test fun setTrustState_updates_row() = runBlocking {
        val dao = db.contactDao()
        dao.upsert(contact("c", "Carl"))
        dao.setTrustState("c", TrustState.VERIFIED.name, 99L)
        val row = dao.getById("c")
        assertThat(row?.trustState).isEqualTo(TrustState.VERIFIED)
        assertThat(row?.updatedAtMs).isEqualTo(99L)
    }
}
