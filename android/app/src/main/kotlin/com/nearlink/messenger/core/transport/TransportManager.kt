package com.nearlink.messenger.core.transport

import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.lan.LanTransport
import com.nearlink.messenger.core.network.WebSocketEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 多通道传输管理：
 *  - 入站：合并所有 [Transport.incoming] 到一条流。
 *  - 出站：按策略选 BT / WS / 失败回退；返回每条 [DeliveryAck]。
 *  - ACK：合并蓝牙与服务器各自的 ackEvents 输出，便于 Repository 归一更新状态。
 *
 * 选路策略与 docs/architecture.md §6 对齐。
 */
@Singleton
class TransportManager @Inject constructor(
    private val bt: BluetoothEngine,
    private val lan: LanTransport,
    private val ws: WebSocketEngine,
) {

    private val scope = CoroutineScope(SupervisorJob())

    private val mergedIncoming = MutableSharedFlow<Envelope>(extraBufferCapacity = 256)
    private val mergedAcks = MutableSharedFlow<DeliveryAck>(extraBufferCapacity = 256)

    init {
        scope.launch {
            merge(bt.incoming(), lan.incoming(), ws.incoming())
                .onEach { mergedIncoming.emit(it) }
                .collect { /* 终止 */ }
        }
        scope.launch {
            merge(bt.ackEvents(), lan.ackEvents(), ws.ackEvents())
                .onEach { mergedAcks.emit(it) }
                .collect { /* 终止 */ }
        }
    }

    fun incoming(): SharedFlow<Envelope> = mergedIncoming.asSharedFlow()
    fun ackEvents(): SharedFlow<DeliveryAck> = mergedAcks.asSharedFlow()

    /**
     * 选择通道并发送一次。返回的流会先发出 Sent/Queued/Failed 之一。
     * Delivered 会通过 [ackEvents] 异步到达。
     */
    suspend fun send(envelope: Envelope, prefer: TransportChannel = TransportChannel.AUTO): Flow<DeliveryAck> {
        val channel = pickChannel(envelope.toDeviceId, prefer)
        return when (channel) {
            TransportChannel.BLUETOOTH -> bt.send(envelope)
            TransportChannel.WIFI_LAN -> lan.send(envelope)
            TransportChannel.SERVER -> ws.send(envelope)
            TransportChannel.AUTO -> {
                // 双通道都不可用：返回 retryable failure；Repository 会保持 PENDING 等下次。
                kotlinx.coroutines.flow.flowOf(
                    DeliveryAck.Failed(envelope.clientMsgId, "no_channel_available", retryable = true)
                )
            }
        }
    }

    /** 暴露给 Repository / Worker：纯查询，不引发 IO。 */
    fun pickChannel(peerDeviceId: String, prefer: TransportChannel = TransportChannel.AUTO): TransportChannel {
        // 显式指定：尊重，但若不可用降级到 AUTO 决策
        if (prefer == TransportChannel.BLUETOOTH && bt.isAvailable(peerDeviceId)) return TransportChannel.BLUETOOTH
        if (prefer == TransportChannel.WIFI_LAN && lan.isAvailable(peerDeviceId)) return TransportChannel.WIFI_LAN
        if (prefer == TransportChannel.SERVER && ws.isAvailable(peerDeviceId)) return TransportChannel.SERVER
        return when {
            lan.isAvailable(peerDeviceId) -> TransportChannel.WIFI_LAN
            bt.isAvailable(peerDeviceId) -> TransportChannel.BLUETOOTH
            ws.isAvailable(peerDeviceId) -> TransportChannel.SERVER
            else -> TransportChannel.AUTO
        }
    }

    fun shutdown() {
        scope.cancel()
        bt.shutdown()
        lan.shutdown()
        ws.disconnect()
    }
}
