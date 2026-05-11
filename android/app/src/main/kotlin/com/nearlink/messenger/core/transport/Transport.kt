package com.nearlink.messenger.core.transport

import kotlinx.coroutines.flow.Flow

/**
 * 单一通道抽象。BluetoothEngine 与 WebSocketEngine 都实现它。
 * TransportManager 在其之上做选路与聚合。
 */
interface Transport {
    val name: TransportChannel

    /** 是否当前可用（WS 已认证 / 蓝牙已 pair 并连通）。 */
    fun isAvailable(peerDeviceId: String): Boolean

    /**
     * 发送一条密文消息。
     * 返回的 ack 流可能先 [DeliveryAck.Sent] 再 [DeliveryAck.Delivered]。
     * 失败情况下发出 [DeliveryAck.Failed] 并结束流。
     */
    suspend fun send(envelope: Envelope): Flow<DeliveryAck>

    /** 入站密文消息（未解密）。供 ReceiveMessageUseCase 订阅。 */
    fun incoming(): Flow<Envelope>
}
