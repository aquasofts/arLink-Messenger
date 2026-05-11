package com.nearlink.messenger.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WS 心跳：每 [intervalMs] 发一次 ping；超过 [timeoutMs] 没收到任何帧 → 触发 onDead。
 *
 * 简单实现：由 WebSocketEngine 在每收到任意帧时 [touch]，timer 计算上次 touch 距今的时长。
 */
@Singleton
class WsHeartbeat @Inject constructor() {

    private var job: Job? = null

    @Volatile private var lastFrameAtMs: Long = 0

    fun start(
        scope: CoroutineScope,
        intervalMs: Long = 30_000L,
        timeoutMs: Long = 90_000L,
        ping: suspend () -> Unit,
        onDead: () -> Unit,
    ) {
        stop()
        lastFrameAtMs = System.currentTimeMillis()
        job = scope.launch {
            while (true) {
                delay(intervalMs)
                runCatching { ping() }
                if (System.currentTimeMillis() - lastFrameAtMs > timeoutMs) {
                    onDead()
                    break
                }
            }
        }
    }

    fun touch() { lastFrameAtMs = System.currentTimeMillis() }

    fun stop() {
        job?.cancel()
        job = null
    }
}
