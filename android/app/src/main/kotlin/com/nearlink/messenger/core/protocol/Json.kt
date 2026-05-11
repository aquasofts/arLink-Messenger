package com.nearlink.messenger.core.protocol

import kotlinx.serialization.json.Json

/** 全工程共享的 JSON 实例。任何序列化都用这个，避免散落配置。 */
val NLJson: Json = Json {
    ignoreUnknownKeys = true        // 服务器/对端可以加新字段而不破坏老客户端
    encodeDefaults = false
    explicitNulls = false
    classDiscriminator = "type"
    isLenient = false
}
