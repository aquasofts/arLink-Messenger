package com.nearlink.messenger.transport

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.network.WebSocketEngine
import com.nearlink.messenger.core.transport.TransportChannel
import com.nearlink.messenger.core.transport.TransportManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class TransportManagerTest {

    private val bt = mockk<BluetoothEngine>(relaxed = true)
    private val ws = mockk<WebSocketEngine>(relaxed = true)

    private fun manager() = TransportManager(bt, ws)

    @Test
    fun `prefers bluetooth when both available`() {
        every { bt.isAvailable("peer1") } returns true
        every { ws.isAvailable("peer1") } returns true
        assertThat(manager().pickChannel("peer1")).isEqualTo(TransportChannel.BLUETOOTH)
    }

    @Test
    fun `falls back to server when bt unavailable`() {
        every { bt.isAvailable("peer1") } returns false
        every { ws.isAvailable("peer1") } returns true
        assertThat(manager().pickChannel("peer1")).isEqualTo(TransportChannel.SERVER)
    }

    @Test
    fun `returns AUTO sentinel when neither available`() {
        every { bt.isAvailable("peer1") } returns false
        every { ws.isAvailable("peer1") } returns false
        assertThat(manager().pickChannel("peer1")).isEqualTo(TransportChannel.AUTO)
    }

    @Test
    fun `prefer SERVER even when bt available if explicitly requested`() {
        every { bt.isAvailable("peer1") } returns true
        every { ws.isAvailable("peer1") } returns true
        assertThat(manager().pickChannel("peer1", TransportChannel.SERVER))
            .isEqualTo(TransportChannel.SERVER)
    }

    @Test
    fun `prefer SERVER but fall back when ws not available`() {
        every { bt.isAvailable("peer1") } returns true
        every { ws.isAvailable("peer1") } returns false
        // 显式请求 SERVER，但不可用 → 走 AUTO 决策 → BLUETOOTH
        assertThat(manager().pickChannel("peer1", TransportChannel.SERVER))
            .isEqualTo(TransportChannel.BLUETOOTH)
    }
}
