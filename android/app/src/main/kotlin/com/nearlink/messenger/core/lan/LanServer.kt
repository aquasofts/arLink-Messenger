package com.nearlink.messenger.core.lan

import com.nearlink.messenger.core.qr.QrEnvelopeCodec
import com.nearlink.messenger.core.transport.Envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanServer @Inject constructor(
    private val codec: QrEnvelopeCodec,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val incoming = MutableSharedFlow<Envelope>(extraBufferCapacity = 64)
    private var job: Job? = null
    private var server: ServerSocket? = null

    val port: Int get() = server?.localPort ?: 0

    fun incoming(): SharedFlow<Envelope> = incoming.asSharedFlow()

    fun start() {
        if (job?.isActive == true) return
        val socket = ServerSocket(0)
        server = socket
        job = scope.launch {
            while (isActive) {
                val client = socket.accept()
                launch {
                    client.use {
                        val line = BufferedReader(InputStreamReader(it.getInputStream())).readLine() ?: return@use
                        codec.decode(line)?.let { envelope -> incoming.emit(envelope) }
                        it.getOutputStream().write("OK\n".toByteArray())
                        it.getOutputStream().flush()
                    }
                }
            }
        }
    }

    fun shutdown() {
        job?.cancel()
        runCatching { server?.close() }
        server = null
        scope.cancel()
    }
}
