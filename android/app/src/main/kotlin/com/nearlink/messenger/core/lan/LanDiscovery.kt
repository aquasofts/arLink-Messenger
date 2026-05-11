package com.nearlink.messenger.core.lan

import android.util.Log
import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.protocol.NLJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanDiscovery @Inject constructor(
    private val identity: IdentityKeyStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peers = MutableSharedFlow<LanPeer>(replay = 1, extraBufferCapacity = 64)
    private var job: Job? = null
    @Volatile private var serverPort: Int = 0

    fun peers(): SharedFlow<LanPeer> = peers.asSharedFlow()

    fun start(port: Int) {
        serverPort = port
        if (job?.isActive == true) return
        job = scope.launch {
            val socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            try {
                launch { receiveLoop(socket) }
                launch { advertiseLoop(socket) }
            } finally {
                socket.close()
            }
        }
    }

    private suspend fun advertiseLoop(socket: DatagramSocket) {
        while (scope.isActive) {
            runCatching {
                val pub = identity.loadPublic()
                val hello = NLJson.encodeToString(
                    LanHello.serializer(),
                    LanHello(
                        deviceId = pub.deviceId,
                        port = serverPort,
                        edPub = CryptoUtils.b64(pub.edPub),
                        xPub = CryptoUtils.b64(pub.xPub),
                    )
                )
                val bytes = hello.toByteArray()
                socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
            }.onFailure { Log.w(TAG, "lan advertise failed: $it") }
            delay(5_000L)
        }
    }

    private suspend fun receiveLoop(socket: DatagramSocket) {
        val self = identity.loadPublic().deviceId
        val buf = ByteArray(2048)
        while (scope.isActive) {
            runCatching {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val hello = NLJson.decodeFromString(LanHello.serializer(), String(packet.data, packet.offset, packet.length))
                if (hello.version == 1 && hello.deviceId != self && hello.port > 0) {
                    peers.emit(
                        LanPeer(
                            deviceId = hello.deviceId,
                            host = packet.address.hostAddress ?: return@runCatching,
                            port = hello.port,
                            nickname = hello.nickname,
                            edPub = hello.edPub,
                            xPub = hello.xPub,
                        )
                    )
                }
            }.onFailure { Log.w(TAG, "lan discovery receive failed: $it") }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    companion object {
        const val DISCOVERY_PORT = 38641
        private const val TAG = "LanDiscovery"
    }
}
