package com.nearlink.messenger.core.network

import android.util.Log
import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.protocol.WireEncryptedMessage
import com.nearlink.messenger.core.protocol.WireFrame
import com.nearlink.messenger.core.protocol.WireFrameTypes
import com.nearlink.messenger.core.transport.DeliveryAck
import com.nearlink.messenger.core.transport.Envelope
import com.nearlink.messenger.core.transport.Transport
import com.nearlink.messenger.core.transport.TransportChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 与自建服务器的 WebSocket 长连接。也实现 [Transport]，在传输层和蓝牙并列。
 *
 * 状态机：DISCONNECTED → CONNECTING → AUTHENTICATING → CONNECTED → DISCONNECTED
 */
@Singleton
class WebSocketEngine @Inject constructor(
    private val identity: IdentityKeyStore,
    private val authenticator: WsAuthenticator,
    private val codec: WsMessageCodec,
    private val heartbeat: WsHeartbeat,
    private val http: OkHttpClient,
) : Transport {

    override val name: TransportChannel = TransportChannel.SERVER

    enum class State { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val incomingFlow = MutableSharedFlow<Envelope>(extraBufferCapacity = 256)
    private val ackFlow = MutableSharedFlow<DeliveryAck>(extraBufferCapacity = 256)
    private val presenceFlow = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 64)
    private val pullChunkFlow = MutableSharedFlow<PullChunkEvent>(extraBufferCapacity = 16)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ws: WebSocket? = null
    private var loopJob: Job? = null
    @Volatile private var currentBase: String? = null
    @Volatile private var retryAttempt: Int = 0

    fun observePresence(): SharedFlow<PresenceEvent> = presenceFlow.asSharedFlow()
    fun observePullChunks(): SharedFlow<PullChunkEvent> = pullChunkFlow.asSharedFlow()
    fun ackEvents(): SharedFlow<DeliveryAck> = ackFlow.asSharedFlow()

    override fun isAvailable(peerDeviceId: String): Boolean = _state.value == State.CONNECTED

    override fun incoming(): Flow<Envelope> = incomingFlow.asSharedFlow()

    /**
     * 连接到指定服务器（httpsBase 形如 `https://example.com` 或 `wss://example.com/v1/ws`）。
     * 幂等：若已连同样地址则忽略。
     */
    fun connect(httpsBase: String) {
        if (currentBase == httpsBase && _state.value == State.CONNECTED) return
        currentBase = httpsBase
        loopJob?.cancel()
        loopJob = scope.launch { connectLoop(httpsBase) }
    }

    fun disconnect() {
        currentBase = null
        loopJob?.cancel()
        ws?.close(1000, "client")
        ws = null
        heartbeat.stop()
        _state.value = State.DISCONNECTED
    }

    private suspend fun connectLoop(httpsBase: String) {
        while (currentBase == httpsBase) {
            try {
                _state.value = State.CONNECTING
                openOnce(httpsBase)
                // openOnce 内部会在 onFailure/onClosed 后返回，循环重试
            } catch (t: Throwable) {
                Log.w(TAG, "ws connect error: $t")
            }
            // 退避：1, 2, 4, 8, ... 60s + jitter
            retryAttempt = (retryAttempt + 1).coerceAtMost(6)
            val backoff = minOf((1 shl retryAttempt) * 1000L, 60_000L)
            val jitter = (Math.random() * 500).toLong()
            delay(backoff + jitter)
        }
    }

    private suspend fun openOnce(httpsBase: String) {
        val auth = authenticator.buildAuthHeader(httpsBase)
        val wsUrl = authenticator.toWsUrl(httpsBase)
        val req = Request.Builder()
            .url(wsUrl)
            .addHeader("X-NL-Auth", auth)
            .build()
        _state.value = State.AUTHENTICATING

        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                ws = webSocket
                heartbeat.start(
                    scope = scope,
                    ping = {
                        runCatching {
                            val frame = codec.buildPing(identity.loadPublic().deviceId)
                            webSocket.send(codec.encode(frame))
                        }
                    },
                    onDead = {
                        Log.w(TAG, "ws heartbeat dead, closing")
                        webSocket.close(4000, "heartbeat_timeout")
                    },
                )
                // 等 server_hello 才把状态切到 CONNECTED
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                heartbeat.touch()
                handleText(text)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                heartbeat.touch()
                handleText(bytes.utf8())
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ws = null
                heartbeat.stop()
                _state.value = State.DISCONNECTED
                if (!done.isCompleted) done.complete(Unit)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "ws failure: $t")
                ws = null
                heartbeat.stop()
                _state.value = State.DISCONNECTED
                if (!done.isCompleted) done.complete(Unit)
            }
        }
        http.newWebSocket(req, listener)
        done.await()
    }

    private fun handleText(text: String) {
        val frame = runCatching { codec.decode(text) }.getOrElse {
            Log.w(TAG, "bad ws frame: $it"); return
        }
        when (frame.type) {
            WireFrameTypes.SERVER_HELLO -> {
                retryAttempt = 0
                _state.value = State.CONNECTED
                // server_hello 之后立即拉离线（拉取 since_ts 由 service 层管理）
            }
            WireFrameTypes.PONG -> { /* heartbeat 已 touch */ }
            WireFrameTypes.MSG_RELAY -> {
                val msg = codec.asEncryptedMessage(frame)
                val from = frame.from ?: return
                val env = Envelope(
                    clientMsgId = msg.clientMsgId,
                    convId = msg.convId,
                    fromDeviceId = from,
                    toDeviceId = msg.toDeviceId,
                    alg = msg.alg,
                    nonce = CryptoUtils.unb64(msg.nonceB64),
                    ephemeralPub = CryptoUtils.unb64(msg.ephemeralPubB64),
                    ciphertext = CryptoUtils.unb64(msg.ciphertextB64),
                    aad = "${from}|${msg.toDeviceId}|${msg.convId}|${msg.clientMsgId}".toByteArray(),
                    refMsgId = msg.refMsgId,
                    createdAtMs = frame.ts,
                )
                scope.launch { incomingFlow.emit(env) }
            }
            WireFrameTypes.MSG_ACK -> {
                val ack = codec.asMsgAck(frame)
                val event = when (ack.status) {
                    "queued" -> DeliveryAck.Queued(ack.clientMsgId, frame.ts)
                    "relayed" -> DeliveryAck.Sent(ack.clientMsgId, name, frame.ts)
                    "rejected" -> DeliveryAck.Failed(ack.clientMsgId, ack.reason ?: "rejected", retryable = false)
                    else -> DeliveryAck.Sent(ack.clientMsgId, name, frame.ts)
                }
                scope.launch { ackFlow.emit(event) }
            }
            WireFrameTypes.MSG_DELIVERED -> {
                val d = codec.asMsgDelivered(frame)
                scope.launch { ackFlow.emit(DeliveryAck.Delivered(d.serverMsgId, name, frame.ts)) }
            }
            WireFrameTypes.PRESENCE_UPDATE -> {
                val p = codec.asPresence(frame)
                scope.launch {
                    presenceFlow.emit(
                        PresenceEvent(deviceId = p.deviceId, state = p.state, lastSeen = p.lastSeen)
                    )
                }
            }
            WireFrameTypes.PULL_OFFLINE_CHUNK -> {
                val chunk = codec.asPullOfflineChunk(frame)
                scope.launch { pullChunkFlow.emit(PullChunkEvent(chunk.messages, chunk.hasMore, chunk.cursor)) }
            }
            WireFrameTypes.ERROR -> {
                val err = codec.asError(frame)
                Log.w(TAG, "server error: ${err.code} ${err.message}")
            }
        }
    }

    override suspend fun send(envelope: Envelope): Flow<DeliveryAck> = flow {
        val socket = ws
        if (socket == null || _state.value != State.CONNECTED) {
            emit(DeliveryAck.Failed(envelope.clientMsgId, "ws_not_connected", retryable = true))
            return@flow
        }
        val payload = WireEncryptedMessage(
            clientMsgId = envelope.clientMsgId,
            convId = envelope.convId,
            toDeviceId = envelope.toDeviceId,
            alg = envelope.alg,
            nonceB64 = CryptoUtils.b64(envelope.nonce),
            ephemeralPubB64 = CryptoUtils.b64(envelope.ephemeralPub),
            ciphertextB64 = CryptoUtils.b64(envelope.ciphertext),
            refMsgId = envelope.refMsgId,
            size = envelope.ciphertext.size,
        )
        val frame = codec.buildMsgSend(envelope.fromDeviceId, payload)
        val ok = socket.send(codec.encode(frame))
        if (!ok) {
            emit(DeliveryAck.Failed(envelope.clientMsgId, "ws_send_failed", retryable = true))
            return@flow
        }
        // Sent/Delivered/Queued/Failed 通过服务器 ack 帧异步抵达，订阅方应同时听 [ackEvents]。
        emit(DeliveryAck.Sent(envelope.clientMsgId, name, System.currentTimeMillis()))
    }

    fun pullOffline(sinceTs: Long) {
        val socket = ws ?: return
        scope.launch {
            val from = identity.loadPublic().deviceId
            socket.send(codec.encode(codec.buildPullOffline(from, sinceTs)))
        }
    }

    fun subscribePresence(deviceIds: List<String>) {
        val socket = ws ?: return
        scope.launch {
            val from = identity.loadPublic().deviceId
            socket.send(codec.encode(codec.buildPresenceSub(from, deviceIds)))
        }
    }

    companion object { private const val TAG = "WebSocketEngine" }
}

data class PresenceEvent(val deviceId: String, val state: String, val lastSeen: Long?)
data class PullChunkEvent(
    val messages: List<kotlinx.serialization.json.JsonElement>,
    val hasMore: Boolean,
    val cursor: String?,
)
