package com.nearlink.messenger.core.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 附件本地存储。所有附件落 app-private 内部目录，避免外部进程访问。
 *
 * 目录布局：
 *   files/attachments/{deviceId}/{file_id}      —— 完整文件（解密后）
 *   files/attachments/{deviceId}/{file_id}.part —— 分片缓冲（下载中）
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val root: File = File(ctx.filesDir, "attachments").apply { mkdirs() }

    fun fileFor(peerDeviceId: String, fileId: String): File {
        val dir = File(root, peerDeviceId).apply { mkdirs() }
        return File(dir, fileId)
    }

    fun partFile(peerDeviceId: String, fileId: String): File {
        val dir = File(root, peerDeviceId).apply { mkdirs() }
        return File(dir, "$fileId.part")
    }

    fun usageBytes(): Long {
        var sum = 0L
        root.walkTopDown().forEach { if (it.isFile) sum += it.length() }
        return sum
    }
}
