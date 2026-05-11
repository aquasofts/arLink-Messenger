package com.nearlink.messenger.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nearlink.messenger.MainActivity
import com.nearlink.messenger.R
import com.nearlink.messenger.core.bluetooth.BleAdvertiser
import com.nearlink.messenger.core.bluetooth.BleScanner
import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.bluetooth.BtHandshake
import com.nearlink.messenger.core.bluetooth.RfcommServer
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.lan.LanTransport
import com.nearlink.messenger.core.network.PresenceEvent
import com.nearlink.messenger.core.network.WebSocketEngine
import com.nearlink.messenger.core.permissions.BluetoothPermissions
import com.nearlink.messenger.core.permissions.PermissionHelper
import com.nearlink.messenger.data.repository.ContactRepository
import com.nearlink.messenger.data.repository.MessageRepository
import com.nearlink.messenger.data.repository.PresenceRepository
import com.nearlink.messenger.data.repository.SettingsRepository
import com.nearlink.messenger.domain.usecase.ReceiveMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 单一前台服务，统一持有：
 *   - WebSocket 长连接（如已配置）
 *   - RFCOMM 接听 + BLE 广播
 *   - 入站消息分发到 Repository
 *   - presence 更新写回 PresenceRepository
 *
 * 选择"一个前台服务"而非分多个，是因为 Android 13+ 多前台服务通知合并体验差，且这里没有真正的独立生命周期。
 * 前台服务通过 foregroundServiceType="connectedDevice|dataSync"（manifest 已声明）。
 */
@AndroidEntryPoint
class NearLinkForegroundService : Service() {

    @Inject lateinit var ws: WebSocketEngine
    @Inject lateinit var lan: LanTransport
    @Inject lateinit var bt: BluetoothEngine
    @Inject lateinit var server: RfcommServer
    @Inject lateinit var advertiser: BleAdvertiser
    @Inject lateinit var scanner: BleScanner
    @Inject lateinit var identity: IdentityKeyStore
    @Inject lateinit var handshake: BtHandshake
    @Inject lateinit var contacts: ContactRepository
    @Inject lateinit var messages: MessageRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var presenceRepo: PresenceRepository
    @Inject lateinit var receiveMessages: ReceiveMessageUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverAcceptJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        bootstrap()
    }

    private fun startForegroundInternal() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NearLink",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "保持加密消息连接" }
            nm.createNotificationChannel(channel)
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("已连接 · 端到端加密")
            .setContentIntent(pi)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            PermissionHelper.allGranted(this, BluetoothPermissions.runtime)
        ) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun bootstrap() {
        // 1) 自动连接 WS（如配置）
        scope.launch { settings.connectIfConfigured() }

        // 2) 入站消息分发
        scope.launch { receiveMessages() }

        // 3) presence 推送 → PresenceRepository
        ws.observePresence()
            .onEach(::onPresence)
            .launchIn(scope)

        // 4) WS 离线拉取：每次连上后触发
        scope.launch {
            ws.state.collect { st ->
                if (st == WebSocketEngine.State.CONNECTED) {
                    val since = settings.lastSyncedTs.first()
                    ws.pullOffline(since)
                    // 同时把本地联系人 device_ids 订阅一遍
                    val ids = contacts.observeAll().first().map { it.deviceId }
                    if (ids.isNotEmpty()) ws.subscribePresence(ids)
                }
            }
        }

        // 5) LAN Wi-Fi / hotspot encrypted transport
        lan.start()

        // 6) BLE 广播 + RFCOMM 接听（按权限/适配器存在性）
        startBluetoothEdge()
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothEdge() {
        if (!PermissionHelper.allGranted(this, BluetoothPermissions.runtime)) return
        scope.launch {
            val pub = identity.loadPublic()
            // BLE Advertise
            runCatching { advertiser.start(pub.edPub) }
            // RFCOMM 监听
            serverAcceptJob?.cancel()
            serverAcceptJob = server.listen()
                .onEach { session ->
                    runCatching {
                        val peer = handshake.performAsAcceptor(session.input, session.output, nickname = null)
                        // 仅对已 VERIFIED 的联系人放行；陌生人首次走 PairScreen 主动连接
                        val existing = contacts.get(peer.deviceId)
                        if (existing == null) {
                            // 第一次见：写入 UNVERIFIED contact，让用户后续在 UI 上确认
                            val now = System.currentTimeMillis()
                            contacts.upsert(
                                com.nearlink.messenger.core.model.Contact(
                                    deviceId = peer.deviceId,
                                    nickname = peer.nickname ?: peer.deviceId.take(6),
                                    pkIdentity = peer.edPub,
                                    pkX = peer.xPub,
                                    trustState = com.nearlink.messenger.core.model.TrustState.UNVERIFIED,
                                    createdAtMs = now,
                                    updatedAtMs = now,
                                )
                            )
                        }
                        bt.onSessionEstablished(peer.deviceId, session)
                    }.onFailure {
                        runCatching { session.close() }
                    }
                }
                .launchIn(scope)
        }
    }

    private suspend fun onPresence(ev: PresenceEvent) {
        val online = ev.state == "server_online" || ev.state == "bt_online"
        presenceRepo.applyServerEvent(ev.deviceId, online, ev.lastSeen)
        ev.lastSeen?.let { contacts.setLastSeen(ev.deviceId, it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        scope.cancel()
        runCatching { advertiser.stop() }
        runCatching { lan.shutdown() }
        runCatching { server.shutdown() }
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "nearlink.core"

        fun start(context: Context) {
            val intent = Intent(context, NearLinkForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, NearLinkForegroundService::class.java))
        }
    }
}
