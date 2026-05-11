package com.nearlink.messenger.core.bluetooth

import com.nearlink.messenger.core.crypto.CryptoUtils
import com.nearlink.messenger.core.crypto.Ed25519X25519
import com.nearlink.messenger.core.crypto.IdentityKeyStore
import com.nearlink.messenger.core.protocol.BtHelloAckPayload
import com.nearlink.messenger.core.protocol.BtHelloPayload
import com.nearlink.messenger.core.protocol.BtPacketType
import com.nearlink.messenger.core.protocol.NLJson
import kotlinx.serialization.encodeToString
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFCOMM 建立后的握手：发 HELLO，收对端 HELLO，互发 HELLO_ACK。
 *
 * 成功后调用方拿到 [PeerIdentity] 即可进入加密配对（safety_number 阶段）。
 */
@Singleton
class BtHandshake @Inject constructor(
    private val identity: IdentityKeyStore,
) {

    suspend fun performAsInitiator(input: InputStream, out: OutputStream, nickname: String?): PeerIdentity {
        sendHello(out, nickname)
        val peer = readHello(input)
        // 双方握手都成功 → 互发 ack
        BtFraming.write(out, BtPacketType.HELLO_ACK,
            NLJson.encodeToString(BtHelloAckPayload(ok = true)).toByteArray())
        // 等对端 ack
        val ackFrame = BtFraming.read(input)
        require(ackFrame.type == BtPacketType.HELLO_ACK) { "expected HELLO_ACK got ${ackFrame.debugTag()}" }
        val ack = NLJson.decodeFromString(BtHelloAckPayload.serializer(), String(ackFrame.payload))
        check(ack.ok) { "peer rejected handshake: ${ack.reason}" }
        return peer
    }

    suspend fun performAsAcceptor(input: InputStream, out: OutputStream, nickname: String?): PeerIdentity {
        val peer = readHello(input)
        sendHello(out, nickname)
        val ackFrame = BtFraming.read(input)
        require(ackFrame.type == BtPacketType.HELLO_ACK) { "expected HELLO_ACK got ${ackFrame.debugTag()}" }
        val ack = NLJson.decodeFromString(BtHelloAckPayload.serializer(), String(ackFrame.payload))
        check(ack.ok) { "peer rejected handshake: ${ack.reason}" }
        BtFraming.write(out, BtPacketType.HELLO_ACK,
            NLJson.encodeToString(BtHelloAckPayload(ok = true)).toByteArray())
        return peer
    }

    private suspend fun sendHello(out: OutputStream, nickname: String?) {
        val pub = identity.loadPublic()
        val sig = identity.sign(("NL-PAIR".toByteArray() + pub.xPub))
        val payload = BtHelloPayload(
            deviceId = pub.deviceId,
            pkIdentityB64 = CryptoUtils.b64(pub.edPub),
            pkXB64 = CryptoUtils.b64(pub.xPub),
            sigB64 = CryptoUtils.b64(sig),
            appVersion = BtUuids.APP_VERSION,
            nickname = nickname,
        )
        BtFraming.write(out, BtPacketType.HELLO, NLJson.encodeToString(payload).toByteArray())
    }

    private fun readHello(input: InputStream): PeerIdentity {
        val frame = BtFraming.read(input)
        require(frame.type == BtPacketType.HELLO) { "expected HELLO got ${frame.debugTag()}" }
        val hello = NLJson.decodeFromString(BtHelloPayload.serializer(), String(frame.payload))

        val edPub = CryptoUtils.unb64(hello.pkIdentityB64)
        val xPub = CryptoUtils.unb64(hello.pkXB64)
        val sig = CryptoUtils.unb64(hello.sigB64)
        // 校验 device_id 是否匹配公钥
        val expected = CryptoUtils.base32Lower(CryptoUtils.sha256(edPub)).take(24)
        require(expected == hello.deviceId) { "device_id mismatch with pk" }
        // 校验签名
        val ok = Ed25519X25519.verify(edPub, "NL-PAIR".toByteArray() + xPub, sig)
        require(ok) { "bad pairing signature" }

        return PeerIdentity(
            deviceId = hello.deviceId,
            edPub = edPub,
            xPub = xPub,
            nickname = hello.nickname,
            appVersion = hello.appVersion,
        )
    }
}

data class PeerIdentity(
    val deviceId: String,
    val edPub: ByteArray,
    val xPub: ByteArray,
    val nickname: String?,
    val appVersion: String,
)
