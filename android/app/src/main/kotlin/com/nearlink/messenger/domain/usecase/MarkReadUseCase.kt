package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.data.repository.MessageRepository
import javax.inject.Inject

class MarkReadUseCase @Inject constructor(
    private val messages: MessageRepository,
) {
    suspend operator fun invoke(convId: String, upToTs: Long = System.currentTimeMillis()) =
        messages.markRead(convId, upToTs)
}
