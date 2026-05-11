package com.nearlink.messenger.core.network

import com.nearlink.messenger.core.protocol.NLJson
import com.nearlink.messenger.core.protocol.WireEncryptedMessage
import com.nearlink.messenger.core.protocol.WireError
import com.nearlink.messenger.core.protocol.WireFrame
import com.nearlink.messenger.core.protocol.WireFrameTypes
import com.nearlink.messenger.core.protocol.WireMsgAck
import com.nearlink.messenger.core.protocol.WireMsgDelivered
import com.nearlink.messenger.core.protocol.WirePresenceUpdate
import com.nearlink.messenger.core.protocol.WirePullOffline
import com.nearlink.messenger.core.protocol.WirePullOfflineChunk
import com.nearlink.messenger.core.protocol.WireServerHello
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket 帧编解码：把高层 payload <-> [WireFrame] 之间互转。
 * 不做副作用，纯函数；方便在 unit test 里走通编/解码。
 */
@Singleton
class WsMessageCodec @Inject constructor() {

    fun encode(frame: WireFrame): String = NLJson.encodeToString(frame)

    fun decode(text: String): WireFrame = NLJson.decodeFromString(WireFrame.serializer(), text)

    // ---------- 业务 helper ----------

    fun newFrameId(): String = UUID.randomUUID().toString()

    fun buildMsgSend(from: String, payload: WireEncryptedMessage): WireFrame {
        val obj = NLJson.encodeToJsonElement(WireEncryptedMessage.serializer(), payload) as JsonObject
        return WireFrame(
            type = WireFrameTypes.MSG_SEND,
            id = newFrameId(),
            ts = System.currentTimeMillis(),
            from = from,
            to = payload.toDeviceId,
            payload = obj,
        )
    }

    fun buildPresenceSub(from: String, deviceIds: List<String>): WireFrame {
        val obj = buildJsonObject {
            put("device_ids", NLJson.encodeToJsonElement(deviceIds))
        }
        return WireFrame(
            type = WireFrameTypes.PRESENCE_SUB,
            id = newFrameId(),
            ts = System.currentTimeMillis(),
            from = from,
            payload = obj,
        )
    }

    fun buildPing(from: String): WireFrame = WireFrame(
        type = WireFrameTypes.PING,
        id = newFrameId(),
        ts = System.currentTimeMillis(),
        from = from,
        payload = buildJsonObject { put("nonce", UUID.randomUUID().toString()) },
    )

    fun buildPullOffline(from: String, sinceTs: Long): WireFrame {
        val obj = NLJson.encodeToJsonElement(WirePullOffline.serializer(), WirePullOffline(sinceTs)) as JsonObject
        return WireFrame(
            type = WireFrameTypes.PULL_OFFLINE,
            id = newFrameId(),
            ts = System.currentTimeMillis(),
            from = from,
            payload = obj,
        )
    }

    // ---------- 入站解码 helpers ----------

    fun asServerHello(frame: WireFrame): WireServerHello =
        NLJson.decodeFromJsonElement(WireServerHello.serializer(), frame.payload!!)

    fun asEncryptedMessage(frame: WireFrame): WireEncryptedMessage =
        NLJson.decodeFromJsonElement(WireEncryptedMessage.serializer(), frame.payload!!)

    fun asMsgAck(frame: WireFrame): WireMsgAck =
        NLJson.decodeFromJsonElement(WireMsgAck.serializer(), frame.payload!!)

    fun asMsgDelivered(frame: WireFrame): WireMsgDelivered =
        NLJson.decodeFromJsonElement(WireMsgDelivered.serializer(), frame.payload!!)

    fun asPresence(frame: WireFrame): WirePresenceUpdate =
        NLJson.decodeFromJsonElement(WirePresenceUpdate.serializer(), frame.payload!!)

    fun asPullOfflineChunk(frame: WireFrame): WirePullOfflineChunk =
        NLJson.decodeFromJsonElement(WirePullOfflineChunk.serializer(), frame.payload!!)

    fun asError(frame: WireFrame): WireError =
        NLJson.decodeFromJsonElement(WireError.serializer(), frame.payload!!)
}
