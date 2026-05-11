package com.nearlink.messenger.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM（12B nonce、16B tag）。作为 lazysodium 不可用时的纯 JCE 回退。
 */
@Singleton
class AesGcmCipher @Inject constructor() : AeadCipher {
    override val alg: String = AeadAlg.AES_256_GCM
    override val nonceSize: Int = 12

    private val rng = SecureRandom()

    override fun randomNonce(): ByteArray = ByteArray(nonceSize).also(rng::nextBytes)

    override fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return c.doFinal(plaintext)
    }

    override fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return try {
            c.doFinal(ciphertext)
        } catch (t: Throwable) {
            throw AeadAuthException("aes-gcm open auth failed: ${t.javaClass.simpleName}")
        }
    }
}
