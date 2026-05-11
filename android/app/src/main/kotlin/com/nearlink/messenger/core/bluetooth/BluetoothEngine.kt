package com.nearlink.messenger.core.bluetooth

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.protocol.BtMessagePayload
import com.nearlink.messenger.core.protocol.BtMsgAckPayload
import com.nearlink.messenger.core.protocol.BtPacketType
import com.nearlink.messenger.core.protocol.NLJson
import com.nearlink.messenger.core.transport.DeliveryAck
import com.nearlink.messenger.core.transport.Envelope
import com.nearlink.messenger.core.transport.Transport
import com.nearlink.messenger.core.transport.TransportChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 蓝牙传输引擎：协调发现、握手、RFCOMM 数据通道、入站分发与出站发送。
 *
 * 上层只需要：
 *  - [observeIncoming] 订阅入站密文 envelope
 *  - [send] 发送密文 envelope（如果对端有活跃 [BtSession] 才会成功）
 *  - [onSessionEstablished] / [onSessionClosed] 由 service 层调用，登记会话
 */
@Singleton
class BluetoothEngine @Inject constructor(
    private val handshake: BtHandshake,
) : Transport {

    override val name = TransportChannel.BLUETOOTH

    private val sessions = ConcurrentHashMap<String, ActiveSession>()        // deviceId -> session
    private val incoming = MutableSharedFlow<Envelope>(replay = 0, extraBufferCapacity = 64)
    private val sessionUpdates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun isAvailable(peerDeviceId: String): Boolean = sessions[peerDeviceId] != null

    fun isAnyConnected(): Boolean = sessions.isNotEmpty()

    fun observeSessions(): kotlinx.coroutines.flow.StateFlow<Map<String, Boolean>> = sessionUpdates

    /** 由 service 层在握手成功后登记会话；engine 接管 IO 循环。 */
    fun onSessionEstablished(deviceId: String, session: BtSession) {
        // 关闭已有同对端会话（最新优先）
        sessions.remove(deviceId)?.close()
        val active = ActiveSession(deviceId, session, scope.launch { readLoop(deviceId, session) })
        sessions[deviceId] = active
        publishSessions()
    }

    fun onSessionClosed(deviceId: String) {
        sessions.remove(deviceId)?.close()
        publishSessions()
    }

    override fun incoming(): Flow<Envelope> = incoming.asSharedFlow()

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    override suspend fun send(envelope: Envelope): Flow<DeliveryAck> = flow {
        val active = sessions[envelope.toDeviceId]
        if (active == null) {
            emit(DeliveryAck.Failed(envelope.clientMsgId, "no_bt_session", retryable = true))
            return@flow
        }
        val payload = BtMessagePayload(
            clientMsgId = envelope.clientMsgId,
            convId = envelope.convId,
            toDeviceId = envelope.toDeviceId,
            alg = envelope.alg,
            nonceB64 = CryptoUtils.b64(envelope.nonce),
            ephemeralPubB64 = CryptoUtils.b64(envelope.ephemeralPub),
            ciphertextB64 = CryptoUtils.b64(envelope.ciphertext),
            refMsgId = envelope.refMsgId,
        )
        try {
            synchronized(active.writeLock) {
                BtFraming.write(
                    active.session.output,
                    BtPacketType.MSG,
                    NLJson.encodeToString(payload).toByteArray()
                )
            }
            emit(DeliveryAck.Sent(envelope.clientMsgId, name, System.currentTimeMillis()))
            // Delivered 由对端 MSG_ACK 触发，readLoop 会通过 sessionAckBus 发出
            active.pendingAcks[envelope.clientMsgId] = true
        } catch (t: IOException) {
            Log.w(TAG, "BT send failed: $t")
            emit(DeliveryAck.Failed(envelope.clientMsgId, t.javaClass.simpleName, retryable = true))
            onSessionClosed(envelope.toDeviceId)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun readLoop(peerDeviceId: String, session: BtSession) {
        try {
            while (true) {
                val frame = BtFraming.read(session.input)
                when (frame.type) {
                    BtPacketType.MSG -> handleIncomingMsg(peerDeviceId, session, frame.payload)
                    BtPacketType.MSG_ACK -> handleIncomingAck(peerDeviceId, frame.payload)
                    BtPacketType.PING -> {
                        // pong with same payload
                        synchronized(sessions[peerDeviceId]?.writeLock ?: Any()) {
                            BtFraming.write(session.output, BtPacketType.PONG, frame.payload)
                        }
                    }
                    BtPacketType.PONG -> { /* RTT 统计可选，先忽略 */ }
                    BtPacketType.BYE -> { onSessionClosed(peerDeviceId); break }
                    else -> Log.d(TAG, "bt rx ignored type=${frame.debugTag()}")
                }
            }
        } catch (e: EOFException) {
            Log.i(TAG, "bt session EOF: $peerDeviceId")
        } catch (e: IOException) {
            Log.w(TAG, "bt session io: $peerDeviceId / $e")
        } finally {
            onSessionClosed(peerDeviceId)
        }
    }

    private suspend fun handleIncomingMsg(peerDeviceId: String, session: BtSession, payload: ByteArray) {
        val msg = NLJson.decodeFromString(BtMessagePayload.serializer(), String(payload))
        val env = Envelope(
            clientMsgId = msg.clientMsgId,
            convId = msg.convId,
            fromDeviceId = peerDeviceId,
            toDeviceId = msg.toDeviceId,
            alg = msg.alg,
            nonce = CryptoUtils.unb64(msg.nonceB64),
            ephemeralPub = CryptoUtils.unb64(msg.ephemeralPubB64),
            ciphertext = CryptoUtils.unb64(msg.ciphertextB64),
            aad = "${peerDeviceId}|${msg.toDeviceId}|${msg.convId}|${msg.clientMsgId}".toByteArray(),
            refMsgId = msg.refMsgId,
            createdAtMs = System.currentTimeMillis(),
        )
        incoming.emit(env)
        // 立刻回 ACK
        val ack = BtMsgAckPayload(clientMsgId = msg.clientMsgId, status = "received")
        try {
            synchronized(sessions[peerDeviceId]?.writeLock ?: Any()) {
                BtFraming.write(session.output, BtPacketType.MSG_ACK, NLJson.encodeToString(ack).toByteArray())
            }
        } catch (_: IOException) { /* readLoop 自会清理 */ }
    }

    private fun handleIncomingAck(peerDeviceId: String, payload: ByteArray) {
        val ack = NLJson.decodeFromString(BtMsgAckPayload.serializer(), String(payload))
        val active = sessions[peerDeviceId] ?: return
        if (active.pendingAcks.remove(ack.clientMsgId) != null) {
            scope.launch {
                ackBus.emit(DeliveryAck.Delivered(ack.clientMsgId, name, System.currentTimeMillis()))
            }
        }
    }

    private val ackBus = MutableSharedFlow<DeliveryAck>(replay = 0, extraBufferCapacity = 64)

    /** 订阅蓝牙链路上回来的 Delivered/失败事件。 */
    fun ackEvents(): SharedFlow<DeliveryAck> = ackBus.asSharedFlow()

    private fun publishSessions() {
        sessionUpdates.value = sessions.mapValues { true }
    }

    fun shutdown() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        scope.cancel()
    }

    private class ActiveSession(
        val deviceId: String,
        val session: BtSession,
        private val readJob: Job,
    ) {
        val writeLock = Any()
        val pendingAcks = ConcurrentHashMap<String, Boolean>()
        fun close() {
            readJob.cancel()
            session.close()
            pendingAcks.clear()
        }
    }

    companion object { private const val TAG = "BluetoothEngine" }
}
