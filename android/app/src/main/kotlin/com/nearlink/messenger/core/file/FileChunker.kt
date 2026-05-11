package com.nearlink.messenger.core.file

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * 文件分片器：按 chunkSize 切分；同时计算整体 SHA-256。
 * 与 docs/bluetooth.md §7 / protocol.md §8 对齐。
 */
class FileChunker(val chunkSize: Int = 64 * 1024) {

    data class ChunkPlan(val totalSize: Long, val totalChunks: Int, val sha256: ByteArray)

    fun plan(file: File): ChunkPlan {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(8 * 1024)
        var size = 0L
        file.inputStream().use { ins ->
            var n: Int
            while (ins.read(buf).also { n = it } > 0) {
                md.update(buf, 0, n); size += n
            }
        }
        val total = ((size + chunkSize - 1) / chunkSize).toInt()
        return ChunkPlan(size, total, md.digest())
    }

    fun writeChunk(src: InputStream, idx: Int, sink: OutputStream): Int {
        val offset = idx.toLong() * chunkSize
        val toSkip = offset
        var skipped = 0L
        while (skipped < toSkip) {
            val k = src.skip(toSkip - skipped)
            if (k <= 0) break
            skipped += k
        }
        val buf = ByteArray(chunkSize)
        val n = src.read(buf)
        if (n <= 0) return 0
        sink.write(buf, 0, n)
        return n
    }
}
