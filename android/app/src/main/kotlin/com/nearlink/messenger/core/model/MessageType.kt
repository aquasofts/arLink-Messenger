package com.nearlink.messenger.core.model

/** 消息体 type（与 encryption.md 中 plaintext.type 一致）。 */
enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    AUDIO,
    REVOKE,        // 控制：撤回
    EDIT,          // 控制：编辑
    REACTION,      // 控制：表情回应
    READ_RECEIPT,  // 控制：已读回执（不展示）
    TYPING,        // 控制：正在输入（不入库）
    SYSTEM,        // 系统提示（密钥变化等）
    UNKNOWN,
}

/** 发送/送达状态。 */
enum class MessageStatus {
    PENDING,        // 已入库，未尝试发送
    SENDING,        // 正在某条通道写出
    SENT,           // 出站成功（蓝牙 ACK 或服务器 ack=queued/relayed）
    DELIVERED,      // 对端确认收到
    READ,           // 对端已读
    FAILED,         // 出站失败，超过重试上限
}
