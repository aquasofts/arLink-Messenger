package com.nearlink.messenger.core.file

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object FileHashVerifier {
    fun sha256(input: InputStream): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8 * 1024)
        var n: Int
        while (input.read(buf).also { n = it } > 0) md.update(buf, 0, n)
        return md.digest()
    }

    fun sha256(file: File): ByteArray = file.inputStream().use { sha256(it) }

    fun matches(expected: ByteArray, actual: ByteArray): Boolean {
        if (expected.size != actual.size) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].toInt() xor actual[i].toInt())
        return diff == 0
    }
}
