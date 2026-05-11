package com.nearlink.messenger.core.bluetooth

import java.util.UUID

/** 与 docs/bluetooth.md §2 对齐的固定 UUID。 */
object BtUuids {
    /** BLE 广播服务 UUID。生产环境请替换成新生成的随机 v4。 */
    val SERVICE_UUID: UUID = UUID.fromString("7e2a0001-4f1b-4e2a-9e2a-c0deb1a000c1")

    /** GATT 身份特征：设备 ID + 公钥指纹前缀。 */
    val CHAR_IDENTITY_UUID: UUID = UUID.fromString("7e2a0002-4f1b-4e2a-9e2a-c0deb1a000c2")

    /** RFCOMM SDP 注册 UUID。 */
    val RFCOMM_UUID: UUID = UUID.fromString("7e2a0010-4f1b-4e2a-9e2a-c0deb1a000c0")

    const val RFCOMM_NAME = "NearLinkRfcomm"
    const val BLE_LOCAL_NAME = "NL"
    const val APP_VERSION = "0.1.0"
}
