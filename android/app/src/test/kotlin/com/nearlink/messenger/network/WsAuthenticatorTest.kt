package com.nearlink.messenger.network

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.network.WsAuthenticator
import org.junit.Test

class WsAuthenticatorTest {

    @Test
    fun `challenge url accepts websocket endpoint input`() {
        assertThat(WsAuthenticator.toChallengeUrl("wss://chat.example.com/v1/ws", "dev123"))
            .isEqualTo("https://chat.example.com/v1/auth/challenge?device_id=dev123")
    }

    @Test
    fun `challenge url accepts http base input`() {
        assertThat(WsAuthenticator.toChallengeUrl("http://10.0.2.2:8080", "dev123"))
            .isEqualTo("http://10.0.2.2:8080/v1/auth/challenge?device_id=dev123")
    }

    @Test
    fun `normalizes bare host as https base`() {
        assertThat(WsAuthenticator.normalizeHttpBase("chat.example.com/"))
            .isEqualTo("https://chat.example.com")
    }
}
