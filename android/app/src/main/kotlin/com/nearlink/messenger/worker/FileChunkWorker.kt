package com.nearlink.messenger.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 文件分片上传/下载 Worker。
 *
 * 实现要点（TODO 留作 v1 后续）：
 *   1. inputData 携带 file_id、本地 uri、目标 device_id、方向（up/down）、起始 chunk_idx。
 *   2. 每片 64KiB，先在外层走 AEAD 加密（与文本消息使用相同算法）。
 *   3. 上传：HTTPS POST /v1/files/upload/{file_id}/{idx}，失败 retry。
 *   4. 下载：HTTPS GET /v1/files/{file_id}/{idx}，按 idx 顺序写盘。
 *   5. 完成时校验整体 SHA-256 并通知 MessageRepository 把附件路径写回 messages 表。
 *
 * 这里仅给出可运行骨架；保留 [Result.success] 占位，方便上层 enqueue 流程能跑通。
 */
@HiltWorker
class FileChunkWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // TODO: implement chunked transfer w/ resume + sha256 verification
        return Result.success()
    }
}
