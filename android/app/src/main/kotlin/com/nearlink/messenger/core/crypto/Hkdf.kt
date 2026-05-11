package com.nearlink.messenger.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HKDF-SHA256（RFC 5869）。不依赖 BouncyCastle，用 JDK 的 HmacSHA256。
 */
object Hkdf {

    private const val HASH_LEN = 32
    private const val ALG = "HmacSHA256"

    fun derive(salt: ByteArray?, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "HKDF length out of range: $length" }

        val prk = extract(salt ?: ByteArray(HASH_LEN), ikm)
        return try {
            expand(prk, info, length)
        } finally {
            prk.fill(0)
        }
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALG).apply { init(SecretKeySpec(salt, ALG)) }
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(ALG).apply { init(SecretKeySpec(prk, ALG)) }
        val n = (length + HASH_LEN - 1) / HASH_LEN
        val out = ByteArray(length)
        var prev = ByteArray(0)
        var offset = 0
        for (i in 1..n) {
            mac.reset()
            mac.update(prev)
            mac.update(info)
            mac.update(byteArrayOf(i.toByte()))
            prev = mac.doFinal()
            val toCopy = minOf(HASH_LEN, length - offset)
            System.arraycopy(prev, 0, out, offset, toCopy)
            offset += toCopy
        }
        prev.fill(0)
        return out
    }
}
