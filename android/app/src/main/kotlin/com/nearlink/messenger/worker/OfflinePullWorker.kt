package com.nearlink.messenger.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearlink.messenger.core.network.WebSocketEngine
import com.nearlink.messenger.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 拉取离线消息。
 *
 * 实际"接收 + 入库"由 WebSocketEngine.observePullChunks 的订阅者（前台服务里的 ReceiveMessageUseCase）处理。
 * 本 Worker 只负责"在没有前台服务的兜底场景下"再触发一次拉取信号。
 */
@HiltWorker
class OfflinePullWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val ws: WebSocketEngine,
    private val settings: SettingsRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching {
            val since = settings.lastSyncedTs.first()
            ws.pullOffline(since)
        }
        return Result.success()
    }
}
