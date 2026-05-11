package com.nearlink.messenger.core.bluetooth

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.experimental.and

/**
 * RFCOMM 上的长度前缀分帧（详见 docs/bluetooth.md §6）。
 *
 *  +--------+------+------+----------+----------------+
 *  | MAGIC  | VER  | TYPE | LEN(u32) | payload bytes  |
 *  | 0x4E4C | 0x01 | 0x.. | big-end  | LEN bytes      |
 *  +--------+------+------+----------+----------------+
 *
 *  单帧 LEN ≤ MAX_FRAME_LEN（256KiB）。
 */
object BtFraming {

    val MAGIC: ByteArray = byteArrayOf(0x4E, 0x4C)         // "NL"
    const val VERSION: Byte = 0x01
    const val MAX_FRAME_LEN: Int = 256 * 1024

    fun write(out: OutputStream, type: Byte, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_LEN) { "payload too large: ${payload.size}" }
        val header = ByteArray(8)
        header[0] = MAGIC[0]
        header[1] = MAGIC[1]
        header[2] = VERSION
        header[3] = type
        val len = payload.size
        header[4] = (len ushr 24 and 0xFF).toByte()
        header[5] = (len ushr 16 and 0xFF).toByte()
        header[6] = (len ushr 8 and 0xFF).toByte()
        header[7] = (len and 0xFF).toByte()
        out.write(header)
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }

    /**
     * 阻塞读一帧。EOF 抛 [java.io.EOFException]。
     */
    fun read(input: InputStream): Frame {
        val header = readFully(input, 8)
        if (header[0] != MAGIC[0] || header[1] != MAGIC[1])
            throw IllegalStateException("bad magic")
        val version = header[2]
        if (version != VERSION) throw IllegalStateException("bad version: $version")
        val type = header[3]
        val len = (header[4].toInt() and 0xFF shl 24) or
            (header[5].toInt() and 0xFF shl 16) or
            (header[6].toInt() and 0xFF shl 8) or
            (header[7].toInt() and 0xFF)
        if (len < 0 || len > MAX_FRAME_LEN) throw IllegalStateException("bad len: $len")
        val payload = if (len == 0) ByteArray(0) else readFully(input, len)
        return Frame(type, payload)
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r < 0) throw java.io.EOFException("EOF after $read/$n bytes")
            read += r
        }
        return buf
    }

    data class Frame(val type: Byte, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Frame && type == other.type && payload.contentEquals(other.payload))
        override fun hashCode(): Int = 31 * type.toInt() + payload.contentHashCode()

        fun debugTag(): String = "0x%02X(%dB)".format(type and 0xFF.toByte(), payload.size)
    }
}
