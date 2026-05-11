package com.nearlink.messenger.core.qr

import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.protocol.NLJson
import com.nearlink.messenger.core.transport.Envelope
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QrEnvelopeCodec @Inject constructor() {
    fun encode(envelope: Envelope, senderName: String? = null): String = NLJson.encodeToString(
        QrEnvelopePacket(
            clientMsgId = envelope.clientMsgId,
            convId = envelope.convId,
            fromDeviceId = envelope.fromDeviceId,
            toDeviceId = envelope.toDeviceId,
            alg = envelope.alg,
            nonce = CryptoUtils.b64(envelope.nonce),
            ephemeralPub = CryptoUtils.b64(envelope.ephemeralPub),
            ciphertext = CryptoUtils.b64(envelope.ciphertext),
            aad = CryptoUtils.b64(envelope.aad),
            refMsgId = envelope.refMsgId,
            createdAtMs = envelope.createdAtMs,
            senderName = senderName,
        )
    )

    fun decode(payload: String): Envelope? {
        val packet = runCatching { NLJson.decodeFromString(QrEnvelopePacket.serializer(), payload) }.getOrNull()
            ?: return null
        if (packet.version != 1) return null
        return runCatching {
            Envelope(
                clientMsgId = packet.clientMsgId,
                convId = packet.convId,
                fromDeviceId = packet.fromDeviceId,
                toDeviceId = packet.toDeviceId,
                alg = packet.alg,
                nonce = CryptoUtils.unb64(packet.nonce),
                ephemeralPub = CryptoUtils.unb64(packet.ephemeralPub),
                ciphertext = CryptoUtils.unb64(packet.ciphertext),
                aad = CryptoUtils.unb64(packet.aad),
                refMsgId = packet.refMsgId,
                createdAtMs = packet.createdAtMs,
            )
        }.getOrNull()
    }
}
