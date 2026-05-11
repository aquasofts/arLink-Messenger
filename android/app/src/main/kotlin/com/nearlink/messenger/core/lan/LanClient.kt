package com.nearlink.messenger.core.lan

import com.nearlink.messenger.core.qr.QrEnvelopeCodec
import com.nearlink.messenger.core.transport.Envelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanClient @Inject constructor(
    private val codec: QrEnvelopeCodec,
) {
    suspend fun send(peer: LanPeer, envelope: Envelope): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.host, peer.port), 3_000)
                socket.getOutputStream().write((codec.encode(envelope) + "\n").toByteArray())
                socket.getOutputStream().flush()
                BufferedReader(InputStreamReader(socket.getInputStream())).readLine() == "OK"
            }
        }.getOrDefault(false)
    }
}
