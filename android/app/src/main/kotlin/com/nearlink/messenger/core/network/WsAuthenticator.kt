package com.nearlink.messenger.core.network

import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 处理 WS 升级前的 HTTPS 挑战 + Ed25519 签名。
 * 见 docs/protocol.md §2。
 */
@Singleton
class WsAuthenticator @Inject constructor(
    private val identity: IdentityKeyStore,
    private val http: OkHttpClient,
) {

    @Serializable
    private data class ChallengeResp(
        @SerialName("challenge_id") val challengeId: String,
        @SerialName("nonce_b64") val nonceB64: String,
        @SerialName("server_time") val serverTime: Long,
        @SerialName("expires_in") val expiresIn: Int,
    )

    @Serializable
    private data class AuthPayload(
        @SerialName("device_id") val deviceId: String,
        @SerialName("challenge_id") val challengeId: String,
        @SerialName("pubkey_b64") val pubkeyB64: String,
        val ts: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 取一次 challenge → 签名 → 返回 `X-NL-Auth` 头。
     * 返回 Pair(authHeader, wsUrl).
     */
    suspend fun buildAuthHeader(httpsBase: String): String {
        val pub = identity.loadPublic()
        val httpUrl = httpsBase.trimEnd('/') + "/v1/auth/challenge?device_id=" + pub.deviceId
        val req = Request.Builder().url(httpUrl).get().build()
        val resp = http.newCall(req).execute()
        val body = resp.use {
            require(it.isSuccessful) { "challenge HTTP ${it.code}" }
            it.body?.string() ?: error("empty challenge body")
        }
        val challenge = json.decodeFromString(ChallengeResp.serializer(), body)
        val nonce = CryptoUtils.unb64(challenge.nonceB64)

        val payload = AuthPayload(
            deviceId = pub.deviceId,
            challengeId = challenge.challengeId,
            pubkeyB64 = CryptoUtils.b64(pub.edPub),
            ts = System.currentTimeMillis(),
        )
        val payloadBytes = json.encodeToString(payload).toByteArray()
        val sig = identity.sign(payloadBytes + nonce)
        return CryptoUtils.b64(payloadBytes) + "." + CryptoUtils.b64(sig)
    }

    fun toWsUrl(httpsBase: String): String {
        val base = httpsBase.trimEnd('/')
        return when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://") + "/v1/ws"
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://") + "/v1/ws"
            base.startsWith("wss://") || base.startsWith("ws://") -> {
                if (base.endsWith("/v1/ws")) base else "$base/v1/ws"
            }
            else -> "wss://$base/v1/ws"
        }
    }
}
