package com.nearlink.messenger.core.crypto

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.X25519
import java.security.SecureRandom

/**
 * Ed25519 签名 + X25519 ECDH。
 *
 * 实现说明：
 *  - Tink 的 Ed25519 与 X25519 是两套独立的密钥；不像 libsodium 那样可以从 Ed 私钥派生 X 私钥。
 *  - 所以本工程把 "身份" 拆成 (edPub, edPriv32, xPub, xPriv32)：四段独立字节串。
 *    edPriv 是 Ed25519 的 32 字节种子（Tink 的 `Ed25519Sign(seed)` 入参），
 *    xPriv 是 X25519 的 32 字节私钥。
 *  - 双方握手时同时交换 (edPub, xPub)，用 edPub 验签、用 xPub 做 ECDH。
 *
 * 兼容性：
 *  - device_id 仍然是 base32(sha256(edPub))[:24]，与服务器 auth.DeviceIDFromPubKey 一致。
 *  - WS 认证签名继续用 Ed25519。
 */
object Ed25519X25519 {

    private val rng = SecureRandom()

    data class IdentityKeyPair(
        val edPub: ByteArray,    // 32B
        val edPriv: ByteArray,   // 32B (Tink Ed25519 seed)
        val xPub: ByteArray,     // 32B
        val xPriv: ByteArray,    // 32B
    )

    fun generate(): IdentityKeyPair {
        // Ed25519
        val edSeed = ByteArray(32).also(rng::nextBytes)
        val edKp = Ed25519Sign.KeyPair.newKeyPairFromSeed(edSeed)
        // X25519：用 Tink 独立生成
        val xPriv = X25519.generatePrivateKey()
        val xPub = X25519.publicFromPrivate(xPriv)
        return IdentityKeyPair(
            edPub = edKp.publicKey,
            edPriv = edSeed,
            xPub = xPub,
            xPriv = xPriv,
        )
    }

    /** Tink 的 Ed25519Sign 接受 32B seed。 */
    fun sign(edPriv: ByteArray, message: ByteArray): ByteArray {
        require(edPriv.size == 32) { "ed25519 seed must be 32 bytes" }
        return Ed25519Sign(edPriv).sign(message)
    }

    fun verify(edPub: ByteArray, message: ByteArray, sig: ByteArray): Boolean = try {
        Ed25519Verify(edPub).verify(sig, message)
        true
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: Exception) {
        false
    }

    /** X25519 ECDH。返回 32B 共享秘密（未 hash）。 */
    fun dh(xPriv: ByteArray, peerXPub: ByteArray): ByteArray =
        X25519.computeSharedSecret(xPriv, peerXPub)
}

// 仅为了 import 不出错的便利别名
private typealias GeneralSecurityException = java.security.GeneralSecurityException
