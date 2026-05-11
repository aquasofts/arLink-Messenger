package com.nearlink.messenger.core.qr

import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.crypto.Ed25519X25519
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.core.model.TrustState
import com.nearlink.messenger.core.protocol.NLJson
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrContactCodec @Inject constructor(
    private val identity: IdentityKeyStore,
) {
    suspend fun encodeInvite(nickname: String? = null, now: Long = System.currentTimeMillis()): String {
        val pub = identity.loadPublic()
        val invite = unsignedInvite(
            deviceId = pub.deviceId,
            nickname = nickname,
            edPub = pub.edPub,
            xPub = pub.xPub,
            createdAtMs = now,
        )
        val signature = identity.sign(canonical(invite).toByteArray())
        return NLJson.encodeToString(invite.copy(signature = CryptoUtils.b64(signature)))
    }

    fun decodeContact(payload: String, now: Long = System.currentTimeMillis()): Contact? {
        val invite = runCatching { NLJson.decodeFromString(QrContactInvite.serializer(), payload) }.getOrNull()
            ?: return null
        if (invite.version != 1) return null
        val edPub = runCatching { CryptoUtils.unb64(invite.edPub) }.getOrNull() ?: return null
        val xPub = runCatching { CryptoUtils.unb64(invite.xPub) }.getOrNull() ?: return null
        val signature = runCatching { CryptoUtils.unb64(invite.signature) }.getOrNull() ?: return null
        if (deviceIdOf(edPub) != invite.deviceId) return null
        if (!Ed25519X25519.verify(edPub, canonical(invite).toByteArray(), signature)) return null
        return Contact(
            deviceId = invite.deviceId,
            nickname = invite.nickname?.takeIf { it.isNotBlank() } ?: invite.deviceId.take(8),
            pkIdentity = edPub,
            pkX = xPub,
            trustState = TrustState.UNVERIFIED,
            createdAtMs = now,
            updatedAtMs = now,
        )
    }

    private fun unsignedInvite(
        deviceId: String,
        nickname: String?,
        edPub: ByteArray,
        xPub: ByteArray,
        createdAtMs: Long,
    ) = QrContactInvite(
        deviceId = deviceId,
        nickname = nickname,
        edPub = CryptoUtils.b64(edPub),
        xPub = CryptoUtils.b64(xPub),
        createdAtMs = createdAtMs,
        signature = "",
    )

    private fun canonical(invite: QrContactInvite): String = listOf(
        invite.deviceId,
        invite.nickname.orEmpty(),
        invite.edPub,
        invite.xPub,
        invite.createdAtMs.toString(),
    ).joinToString("|")

    private fun deviceIdOf(edPub: ByteArray): String = CryptoUtils.base32Lower(CryptoUtils.sha256(edPub)).take(24)
}
