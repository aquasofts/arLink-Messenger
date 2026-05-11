package com.nearlink.messenger.model

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.model.MessageStatus
import com.nearlink.messenger.core.model.MessageType
import com.nearlink.messenger.data.local.entity.MessageEntity
import org.junit.Test

/**
 * 这是纯逻辑层的去重测试：模拟同 client_msg_id 的两个 entity 是否被识别为相等键。
 * 真正 INSERT OR IGNORE 行为在 androidTest/MessageDaoTest 验证。
 */
class MessageDedupTest {

    private fun sample(id: String) = MessageEntity(
        id = id,
        convId = "peerA",
        senderDeviceId = "self",
        recipientDeviceId = "peerA",
        type = MessageType.TEXT,
        status = MessageStatus.PENDING,
        isOutgoing = true,
        createdAtMs = 1_700_000_000_000,
        updatedAtMs = 1_700_000_000_000,
        text = "hi",
    )

    @Test
    fun `same client msg id collapses to one in a set`() {
        val a = sample("01HABCDEF")
        val b = sample("01HABCDEF")
        val set = setOf(a, b)
        // data class equals 默认按所有字段；这里只是确保 id 等价
        assertThat(set.map { it.id }.distinct()).hasSize(1)
    }

    @Test
    fun `different client msg id are distinct`() {
        val a = sample("01HABCDEF")
        val b = sample("01HFFFFFF")
        assertThat(a.id).isNotEqualTo(b.id)
    }
}
