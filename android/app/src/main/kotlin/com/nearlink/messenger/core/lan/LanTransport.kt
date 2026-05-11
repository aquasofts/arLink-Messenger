package com.nearlink.messenger.core.lan

import com.nearlink.messenger.core.transport.DeliveryAck
import com.nearlink.messenger.core.transport.Envelope
import com.nearlink.messenger.core.transport.Transport
import com.nearlink.messenger.core.transport.TransportChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanTransport @Inject constructor(
    private val discovery: LanDiscovery,
    private val server: LanServer,
    private val client: LanClient,
) : Transport {
    override val name: TransportChannel = TransportChannel.WIFI_LAN

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = ConcurrentHashMap<String, LanPeer>()
    private val ackFlow = MutableSharedFlow<DeliveryAck>(extraBufferCapacity = 64)

    init {
        scope.launch {
            discovery.peers().collect { peers[it.deviceId] = it }
        }
    }

    fun start() {
        server.start()
        if (server.port > 0) discovery.start(server.port)
    }

    override fun isAvailable(peerDeviceId: String): Boolean = peers.containsKey(peerDeviceId)

    override suspend fun send(envelope: Envelope): Flow<DeliveryAck> = flow {
        val peer = peers[envelope.toDeviceId]
        if (peer == null) {
            emit(DeliveryAck.Failed(envelope.clientMsgId, "lan_peer_not_found", retryable = true))
            return@flow
        }
        if (client.send(peer, envelope)) {
            val ack = DeliveryAck.Sent(envelope.clientMsgId, name, System.currentTimeMillis())
            emit(ack)
            ackFlow.emit(ack)
        } else {
            emit(DeliveryAck.Failed(envelope.clientMsgId, "lan_send_failed", retryable = true))
        }
    }

    override fun incoming(): Flow<Envelope> = server.incoming()

    fun ackEvents(): SharedFlow<DeliveryAck> = ackFlow.asSharedFlow()

    fun shutdown() {
        discovery.shutdown()
        server.shutdown()
        peers.clear()
    }
}
