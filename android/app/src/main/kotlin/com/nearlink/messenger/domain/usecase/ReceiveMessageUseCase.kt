package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.transport.TransportManager
import com.nearlink.messenger.data.repository.MessageRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * 启动入站消息处理循环：监听 TransportManager.incoming → MessageRepository.ingest。
 * 由前台服务持续调用。
 */
class ReceiveMessageUseCase @Inject constructor(
    private val transport: TransportManager,
    private val messages: MessageRepository,
) {
    suspend operator fun invoke() {
        transport.incoming()
            .onEach { runCatching { messages.ingest(it) } }
            .collect { /* terminal */ }
    }
}
