package com.nearlink.messenger.core.database

import androidx.room.TypeConverter
import com.nearlink.messenger.core.model.MessageStatus
import com.nearlink.messenger.core.model.MessageType
import com.nearlink.messenger.core.model.TrustState

class RoomConverters {
    @TypeConverter fun toMsgType(v: String?) = v?.let { runCatching { MessageType.valueOf(it) }.getOrDefault(MessageType.UNKNOWN) }
    @TypeConverter fun fromMsgType(v: MessageType?) = v?.name

    @TypeConverter fun toMsgStatus(v: String?) = v?.let { MessageStatus.valueOf(it) }
    @TypeConverter fun fromMsgStatus(v: MessageStatus?) = v?.name

    @TypeConverter fun toTrust(v: String?) = v?.let { TrustState.valueOf(it) }
    @TypeConverter fun fromTrust(v: TrustState?) = v?.name
}
