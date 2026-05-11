package com.nearlink.messenger.crypto

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.crypto.AeadAuthException
import com.nearlink.messenger.core.crypto.AesGcmCipher
import com.nearlink.messenger.core.crypto.Hkdf
import org.junit.Test

/**
 * 注：当前 AEAD 唯一实现是 AesGcmCipher（JCE）；HKDF 用 JDK HmacSHA256。
 * 不依赖任何 native 库，JVM 单测可直接运行。
 */
class CryptoEngineTest {

    private val aead = AesGcmCipher()

    @Test
    fun `aes-gcm round trip`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = aead.randomNonce()
        val aad = "from|to|conv|msg".toByteArray()
        val plain = "hello world".toByteArray()
        val ct = aead.seal(key, nonce, aad, plain)
        val decrypted = aead.open(key, nonce, aad, ct)
        assertThat(String(decrypted)).isEqualTo("hello world")
    }

    @Test(expected = AeadAuthException::class)
    fun `tamper aad fails open`() {
        val key = ByteArray(32) { 1 }
        val nonce = aead.randomNonce()
        val plain = "secret".toByteArray()
        val ct = aead.seal(key, nonce, "aad-1".toByteArray(), plain)
        aead.open(key, nonce, "aad-2".toByteArray(), ct)
    }

    @Test(expected = AeadAuthException::class)
    fun `tamper ciphertext fails open`() {
        val key = ByteArray(32) { 2 }
        val nonce = aead.randomNonce()
        val plain = "secret".toByteArray()
        val ct = aead.seal(key, nonce, "aad".toByteArray(), plain).copyOf()
        ct[0] = (ct[0].toInt() xor 1).toByte()
        aead.open(key, nonce, "aad".toByteArray(), ct)
    }

    @Test
    fun `hkdf is deterministic`() {
        val salt = "salt".toByteArray()
        val ikm = ByteArray(32) { 0x42 }
        val info = "info".toByteArray()
        val a = Hkdf.derive(salt, ikm, info, 32)
        val b = Hkdf.derive(salt, ikm, info, 32)
        assertThat(a).isEqualTo(b)
        assertThat(a).hasLength(32)
    }

    @Test
    fun `hkdf differs with different info`() {
        val salt = "salt".toByteArray()
        val ikm = ByteArray(32) { 0x42 }
        val a = Hkdf.derive(salt, ikm, "msg-1".toByteArray(), 32)
        val b = Hkdf.derive(salt, ikm, "msg-2".toByteArray(), 32)
        assertThat(a).isNotEqualTo(b)
    }
}
