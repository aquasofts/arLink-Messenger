package com.nearlink.messenger.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nearlink.messenger.data.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 周期消费 outbox。MessageRepository.retryDue 内部按指数退避重算 next_attempt_at_ms。
 */
@HiltWorker
class MessageRetryWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val messages: MessageRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { messages.retryDue(System.currentTimeMillis()) }
            .onFailure { return Result.retry() }
        return Result.success()
    }
}
