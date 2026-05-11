package com.nearlink.messenger.core.crypto

import com.nearlink.messenger.core.transport.Envelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
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
        // ephemeral keypair
        val eph = Ed25519X25519.generate()  // 生成一对，但只用 x 部分
        val ephPub = eph.xPub
        val ephSk = eph.xSk
        try {
            val rootShared = identity.dh(peerXPub)
            val rootKey = deriver.rootKey(selfDeviceId, peerDeviceId, rootShared)
            CryptoUtils.wipe(rootShared)

            val ephShared = Ed25519X25519.dh(ephSk, peerXPub)
            val messageKey = deriver.messageKey(rootKey, ephShared, clientMsgId)
            CryptoUtils.wipe(rootKey, ephShared)

            val nonce = aead.randomNonce()
            val aad = buildAad(selfDeviceId, peerDeviceId, convId, clientMsgId)
            val ct = aead.seal(messageKey, nonce, aad, plaintext)
            CryptoUtils.wipe(messageKey)

            // 长期私钥也要擦除（DH 已 clone，这里只擦 eph）
            CryptoUtils.wipe(eph.edSk, eph.edPub)

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
            CryptoUtils.wipe(ephSk)
        }
    }

    suspend fun open(
        selfDeviceId: String,
        envelope: Envelope,
    ): ByteArray {
        val peerXPubViaContact = envelope.fromDeviceId    // Repository 需要按 from 查 pk_x 传进来
        // 为保持 CryptoEngine 纯净：把 peerXPub 的查找放到 Repository 那一层，
        // 这里要求调用方提前把 envelope.aad 中的 from 对照到其静态 pk_x 之后，才调用 [openWithPeer]。
        throw UnsupportedOperationException("use openWithPeer(selfDeviceId, envelope, peerStaticXPub)")
    }

    /** 调用方负责把发送方的静态 X25519 公钥查出来并传入。 */
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
        fun buildAad(from: String, to: String, convId: String, clientMsgId: String): ByteArray =
            "$from|$to|$convId|$clientMsgId".toByteArray(Charsets.UTF_8)

        fun newClientMsgId(): String = UUID.randomUUID().toString()   // v4；项目内 TODO：换 ULID/UUIDv7
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
