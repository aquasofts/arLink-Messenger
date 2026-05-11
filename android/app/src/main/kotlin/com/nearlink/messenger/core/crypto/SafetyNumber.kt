package com.nearlink.messenger.core.crypto

import java.math.BigInteger

/**
 * 安全码（Safety Number，见 encryption.md §5）。
 *
 * 双方 Ed25519 公钥 → 5200 次 SHA-512 → 各取前 30 字节 → 按字典序拼接 →
 * 解释为十进制大整数 → 去掉"前导 0 的形式等价"并格式化为 12 组 ×5 位。
 *
 * 5200 迭代 + 60 位展示 = 约 200 位安全强度，对照 Signal 设计。
 */
object SafetyNumber {

    private const val ITERATIONS = 5200
    private const val TRIM_BYTES = 30
    private const val GROUPS = 12
    private const val DIGITS_PER_GROUP = 5

    fun compute(pkA: ByteArray, pkB: ByteArray): String {
        val fa = fingerprint(pkA)
        val fb = fingerprint(pkB)
        val concat = if (compareUnsigned(fa, fb) < 0) fa + fb else fb + fa
        return toDigits(concat)
    }

    private fun fingerprint(pk: ByteArray): ByteArray {
        var hash = CryptoUtils.sha512(pk, pk)
        repeat(ITERATIONS - 1) { hash = CryptoUtils.sha512(hash, pk) }
        return hash.copyOf(TRIM_BYTES)
    }

    private fun toDigits(bytes: ByteArray): String {
        // 解释为正整数；若首字节高位为 1 也得当无符号，这里加个 0x00 前缀。
        val bi = BigInteger(1, bytes)
        val modulus = BigInteger.TEN.pow(GROUPS * DIGITS_PER_GROUP)
        val digits = bi.mod(modulus).toString(10).padStart(GROUPS * DIGITS_PER_GROUP, '0')
        return digits.chunked(DIGITS_PER_GROUP).joinToString(" ")
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val min = minOf(a.size, b.size)
        for (i in 0 until min) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }
}
