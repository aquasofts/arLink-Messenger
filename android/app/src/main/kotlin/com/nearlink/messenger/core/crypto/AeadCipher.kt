package com.nearlink.messenger.core.crypto

/**
 * AEAD 抽象。默认实现 [XChaChaPolyCipher]（libsodium）与 [AesGcmCipher]（Tink）二选一。
 * Key 长度固定 32B。Nonce 长度由具体实现决定（XChaCha=24, GCM=12）。
 */
interface AeadCipher {
    val alg: String
    val keySize: Int get() = 32
    val nonceSize: Int

    /** 生成一个随机 nonce。 */
    fun randomNonce(): ByteArray

    /** 返回 ciphertext（含 MAC tag，按算法语义）。 */
    fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray

    /** 解密并验证，失败抛 [AeadAuthException]。 */
    fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
}

class AeadAuthException(msg: String) : RuntimeException(msg)

object AeadAlg {
    const val XCHACHA20_POLY1305 = "xchacha20poly1305"
    const val AES_256_GCM = "aes-256-gcm"
}
