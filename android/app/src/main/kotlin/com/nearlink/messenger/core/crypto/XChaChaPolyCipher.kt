package com.nearlink.messenger.core.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.utils.Key
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * XChaCha20-Poly1305（ietf 版本，24B nonce，16B tag）。
 * 首选实现：libsodium 行为稳定，跨平台互通性好。
 */
@Singleton
class XChaChaPolyCipher @Inject constructor() : AeadCipher {

    override val alg: String = AeadAlg.XCHACHA20_POLY1305
    override val nonceSize: Int = AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES

    private val rng = SecureRandom()
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    override fun randomNonce(): ByteArray = ByteArray(nonceSize).also(rng::nextBytes)

    override fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == keySize) { "bad key size" }
        require(nonce.size == nonceSize) { "bad nonce size" }
        val ct = ByteArray(plaintext.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val outLen = LongArray(1)
        val ok = sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            ct, outLen,
            plaintext, plaintext.size.toLong(),
            aad, aad.size.toLong(),
            null,
            nonce,
            Key.fromBytes(key).asBytes
        )
        check(ok) { "xchacha seal failed" }
        return ct.copyOf(outLen[0].toInt())
    }

    override fun open(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == keySize)
        require(nonce.size == nonceSize)
        val pt = ByteArray(ciphertext.size)
        val outLen = LongArray(1)
        val ok = sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            pt, outLen,
            null,
            ciphertext, ciphertext.size.toLong(),
            aad, aad.size.toLong(),
            nonce,
            Key.fromBytes(key).asBytes
        )
        if (!ok) throw AeadAuthException("xchacha open auth failed")
        return pt.copyOf(outLen[0].toInt())
    }
}
