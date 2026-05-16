package com.nearlink.messenger.core.crypto

import com.nearlink.messenger.core.transport.Envelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 高层加密入口，把 "明文 object" 打包为可走传输层的 [Envelope]，或反向解开。
 *
 * 依赖：
 *  - [IdentityKeyStore]   本机 X25519 静态私钥（短暂使用）
 *  - [SessionKeyDeriver]  root_key / message_key 派生
 *  - [AeadCipher]         具体 AEAD 实现（默认 XChaCha20-Poly1305）
 */
@Singleton
class CryptoEngine @Inject constructor(
    private val identity: IdentityKeyStore,
    private val deriver: SessionKeyDeriver,
    private val aead: AeadCipher,
) {

    /**
     * 用对端 X25519 公钥加密明文消息。
     * 调用方负责：生成 clientMsgId（ULID/UUIDv7）、把 plaintextJson 串好。
     */
    suspend fun seal(
        selfDeviceId: String,
        peerDeviceId: String,
        peerXPub: ByteArray,
        convId: String,
        clientMsgId: String,
        plaintext: ByteArray,
    ): Envelope {
        // 一次性 ephemeral X25519 keypair
        val eph = Ed25519X25519.generate()
        val ephPub = eph.xPub
        val ephPriv = eph.xPriv
        try {
            val rootShared = identity.dh(peerXPub)
            val rootKey = deriver.rootKey(selfDeviceId, peerDeviceId, rootShared)
            CryptoUtils.wipe(rootShared)

            val ephShared = Ed25519X25519.dh(ephPriv, peerXPub)
            val messageKey = deriver.messageKey(rootKey, ephShared, clientMsgId)
            CryptoUtils.wipe(rootKey, ephShared)

            val nonce = aead.randomNonce()
            val aad = buildAad(selfDeviceId, peerDeviceId, convId, clientMsgId)
            val ct = aead.seal(messageKey, nonce, aad, plaintext)
            CryptoUtils.wipe(messageKey)

            // 一次性 Ed25519 部分这里用不上，但仍然擦掉
            CryptoUtils.wipe(eph.edPriv)

            return Envelope(
                clientMsgId = clientMsgId,
                convId = convId,
                fromDeviceId = selfDeviceId,
                toDeviceId = peerDeviceId,
                alg = aead.alg,
                nonce = nonce,
                ephemeralPub = ephPub,
                ciphertext = ct,
                aad = aad,
                createdAtMs = System.currentTimeMillis(),
            )
        } finally {
            CryptoUtils.wipe(ephPriv)
        }
    }

    suspend fun openWithPeer(
        selfDeviceId: String,
        envelope: Envelope,
        peerStaticXPub: ByteArray,
    ): ByteArray {
        val rootShared = identity.dh(peerStaticXPub)
        val rootKey = deriver.rootKey(selfDeviceId, envelope.fromDeviceId, rootShared)
        CryptoUtils.wipe(rootShared)

        // 接收端用自己的静态 X25519 私钥 + 对端发来的 ephemeral pub 再 DH。
        val ephShared = identity.dh(envelope.ephemeralPub)
        val messageKey = deriver.messageKey(rootKey, ephShared, envelope.clientMsgId)
        CryptoUtils.wipe(rootKey, ephShared)

        val cipher = selectAead(envelope.alg)
        val plaintext = try {
            cipher.open(messageKey, envelope.nonce, envelope.aad, envelope.ciphertext)
        } finally {
            CryptoUtils.wipe(messageKey)
        }
        return plaintext
    }

    private fun selectAead(alg: String): AeadCipher =
        if (alg == aead.alg) aead else throw IllegalStateException("unsupported alg: $alg")

    companion object {
        private val random = SecureRandom()
        private var lastTimestampMs = 0L
        private var sequence = 0

        fun buildAad(from: String, to: String, convId: String, clientMsgId: String): ByteArray =
            "$from|$to|$convId|$clientMsgId".toByteArray(Charsets.UTF_8)

        @Synchronized
        fun newClientMsgId(nowMs: Long = System.currentTimeMillis()): String {
            val timestampMs = nowMs.coerceAtLeast(lastTimestampMs)
            sequence = if (timestampMs == lastTimestampMs) (sequence + 1) and 0x0fff else random.nextInt(0x1000)
            lastTimestampMs = timestampMs

            val bytes = ByteArray(16)
            bytes[0] = (timestampMs ushr 40).toByte()
            bytes[1] = (timestampMs ushr 32).toByte()
            bytes[2] = (timestampMs ushr 24).toByte()
            bytes[3] = (timestampMs ushr 16).toByte()
            bytes[4] = (timestampMs ushr 8).toByte()
            bytes[5] = timestampMs.toByte()
            bytes[6] = (0x70 or (sequence ushr 8)).toByte()
            bytes[7] = sequence.toByte()
            random.nextBytes(bytes, 8, 8)
            bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
            return bytes.toUuidString()
        }

        private fun SecureRandom.nextBytes(bytes: ByteArray, offset: Int, length: Int) {
            val randomBytes = ByteArray(length)
            nextBytes(randomBytes)
            randomBytes.copyInto(bytes, offset)
        }

        private fun ByteArray.toUuidString(): String {
            val chars = CharArray(36)
            var charIndex = 0
            for (i in indices) {
                if (i == 4 || i == 6 || i == 8 || i == 10) chars[charIndex++] = '-'
                val value = this[i].toInt() and 0xff
                chars[charIndex++] = HEX[value ushr 4]
                chars[charIndex++] = HEX[value and 0x0f]
            }
            return String(chars)
        }

        private val HEX = "0123456789abcdef".toCharArray()
    }
}

/** 与 JSON 互转的便利层 —— 避免 CryptoEngine 直接耦合业务 schema。 */
class PlaintextCodec @Inject constructor() {
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    inline fun <reified T> encode(value: T): ByteArray =
        json.encodeToString(value).toByteArray(Charsets.UTF_8)

    inline fun <reified T> decode(bytes: ByteArray): T =
        json.decodeFromString(String(bytes, Charsets.UTF_8))
}
