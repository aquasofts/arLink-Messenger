package com.nearlink.messenger.core.crypto

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话密钥派生（见 docs/encryption.md §4）。
 *
 * 与对端一次性协商出 [rootKey]（静态-静态 DH → HKDF），
 * 每条消息再用一次性 ephemeral X25519 + HKDF 派生 [messageKey]。
 */
@Singleton
class SessionKeyDeriver @Inject constructor() {

    /** 静态 root_key：把两个 device_id 按字典序排序，作为 HKDF salt。 */
    fun rootKey(selfDeviceId: String, peerDeviceId: String, staticShared: ByteArray): ByteArray {
        val ids = listOf(selfDeviceId, peerDeviceId).sorted()
        val salt = (ids[0] + "|" + ids[1]).toByteArray(Charsets.UTF_8)
        return Hkdf.derive(salt = salt, ikm = staticShared, info = INFO_ROOT, length = 32)
    }

    /** 每条消息密钥。 */
    fun messageKey(rootKey: ByteArray, ephemeralShared: ByteArray, clientMsgId: String): ByteArray {
        val info = INFO_MSG_PREFIX + clientMsgId.toByteArray(Charsets.UTF_8)
        return Hkdf.derive(salt = rootKey, ikm = ephemeralShared, info = info, length = 32)
    }

    companion object {
        val INFO_ROOT = "NL-ROOT-v1".toByteArray(Charsets.UTF_8)
        val INFO_MSG_PREFIX = "NL-MSG-v1|".toByteArray(Charsets.UTF_8)
    }
}
