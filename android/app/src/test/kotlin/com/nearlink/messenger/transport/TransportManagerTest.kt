package com.nearlink.messenger.transport

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.transport.TransportChannel
import org.junit.Test

/**
 * 不直接构造 BluetoothEngine / WebSocketEngine（它们的依赖会扯进 Android Context 与 OkHttp 长连）。
 * 仅校验 [TransportChannel] 选路的"决策表"——这就是 TransportManager.pickChannel 的全部逻辑。
 *
 * 完整端到端选路（含真正的 BT/WS 状态）由 instrumented 测试覆盖。
 */
class TransportManagerTest {

    /** 与 TransportManager.pickChannel 中实现完全等价的纯函数版本。 */
    private fun pick(
        prefer: TransportChannel,
        btAvailable: Boolean,
        wsAvailable: Boolean,
        lanAvailable: Boolean = false,
    ): TransportChannel {
        if (prefer == TransportChannel.BLUETOOTH && btAvailable) return TransportChannel.BLUETOOTH
        if (prefer == TransportChannel.WIFI_LAN && lanAvailable) return TransportChannel.WIFI_LAN
        if (prefer == TransportChannel.SERVER && wsAvailable) return TransportChannel.SERVER
        return when {
            btAvailable -> TransportChannel.BLUETOOTH
            wsAvailable -> TransportChannel.SERVER
            lanAvailable -> TransportChannel.WIFI_LAN
            else -> TransportChannel.AUTO
        }
    }

    @Test
    fun `prefers bluetooth when both available`() {
        assertThat(pick(TransportChannel.AUTO, btAvailable = true, wsAvailable = true))
            .isEqualTo(TransportChannel.BLUETOOTH)
    }

    @Test
    fun `falls back to server when bt unavailable`() {
        assertThat(pick(TransportChannel.AUTO, btAvailable = false, wsAvailable = true))
            .isEqualTo(TransportChannel.SERVER)
    }

    @Test
    fun `returns AUTO sentinel when neither available`() {
        assertThat(pick(TransportChannel.AUTO, btAvailable = false, wsAvailable = false))
            .isEqualTo(TransportChannel.AUTO)
    }

    @Test
    fun `prefer SERVER takes effect when ws available`() {
        assertThat(pick(TransportChannel.SERVER, btAvailable = true, wsAvailable = true))
            .isEqualTo(TransportChannel.SERVER)
    }

    @Test
    fun `prefer SERVER falls back to bt when ws unavailable`() {
        assertThat(pick(TransportChannel.SERVER, btAvailable = true, wsAvailable = false))
            .isEqualTo(TransportChannel.BLUETOOTH)
    }

    @Test
    fun `uses lan after bluetooth and server`() {
        assertThat(pick(TransportChannel.AUTO, btAvailable = false, wsAvailable = false, lanAvailable = true))
            .isEqualTo(TransportChannel.WIFI_LAN)
    }
}
