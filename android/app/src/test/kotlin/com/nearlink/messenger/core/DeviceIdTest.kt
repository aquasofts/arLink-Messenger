package com.nearlink.messenger.core

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.crypto.CryptoUtils
import org.junit.Test

/**
 * device_id 派生规则：base32(sha256(pk))[:24] 小写，无 padding。
 * 这里不依赖 libsodium，直接用 SHA-256 + base32 验证截断 + 长度。
 */
class DeviceIdTest {

    @Test
    fun `device id length is 24`() {
        val pub = ByteArray(32) { it.toByte() }
        val devId = CryptoUtils.base32Lower(CryptoUtils.sha256(pub)).take(24)
        assertThat(devId).hasLength(24)
    }

    @Test
    fun `device id is deterministic for same pubkey`() {
        val pub = ByteArray(32) { it.toByte() }
        val a = CryptoUtils.base32Lower(CryptoUtils.sha256(pub)).take(24)
        val b = CryptoUtils.base32Lower(CryptoUtils.sha256(pub)).take(24)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `device id changes when pubkey changes by 1 bit`() {
        val pub = ByteArray(32) { it.toByte() }
        val mutated = pub.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val a = CryptoUtils.base32Lower(CryptoUtils.sha256(pub)).take(24)
        val b = CryptoUtils.base32Lower(CryptoUtils.sha256(mutated)).take(24)
        assertThat(a).isNotEqualTo(b)
    }
}
