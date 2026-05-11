package com.nearlink.messenger.core.crypto

import android.util.Base64
import java.security.MessageDigest

/** 加密模块共用的字节工具。所有 Base64 一律 URL-safe, no-padding。 */
object CryptoUtils {

    private const val B64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, B64_FLAGS)

    fun unb64(s: String): ByteArray = Base64.decode(s, B64_FLAGS)

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (p in parts) md.update(p)
        return md.digest()
    }

    fun sha512(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        for (p in parts) md.update(p)
        return md.digest()
    }

    /** RFC 4648 base32，无 padding，小写。只用于 device_id 展示。 */
    fun base32Lower(bytes: ByteArray): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        val out = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                out.append(alphabet[index])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(alphabet[index])
        }
        return out.toString()
    }

    /** 字节清零（尽力而为；JVM 的 GC 不保证不复制）。 */
    fun wipe(vararg arrays: ByteArray?) {
        for (a in arrays) a?.fill(0)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
