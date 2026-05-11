package com.nearlink.messenger.data.repository

import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.network.WebSocketEngine
import com.nearlink.messenger.core.model.PresenceState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聚合 BluetoothEngine 的会话表 与 WebSocketEngine 的 presence 推送 → 对外 [PresenceState] 流。
 */
@Singleton
class PresenceRepository @Inject constructor(
    private val bt: BluetoothEngine,
    private val ws: WebSocketEngine,
) {
    private val serverPresence = MutableStateFlow<Map<String, PresenceState>>(emptyMap())

    init {
        // hooks 由 service 层 collect 后调用，这里仅暴露接口
    }

    fun observe(deviceId: String): Flow<PresenceState> = combine(
        bt.observeSessions().map { it[deviceId] == true },
        serverPresence.map { it[deviceId] }
    ) { btOnline, serverState ->
        PresenceState(
            deviceId = deviceId,
            btOnline = btOnline,
            serverOnline = serverState?.serverOnline ?: false,
            lastSeenMs = serverState?.lastSeenMs,
        )
    }

    fun applyServerEvent(deviceId: String, online: Boolean, lastSeen: Long?) {
        serverPresence.update { current ->
            current + (deviceId to PresenceState(deviceId, false, online, lastSeen))
        }
    }
}
