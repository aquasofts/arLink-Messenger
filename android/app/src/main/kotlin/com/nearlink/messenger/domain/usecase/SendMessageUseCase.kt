package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.model.Message
import com.nearlink.messenger.data.repository.MessageRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messages: MessageRepository,
) {
    suspend operator fun invoke(convId: String, text: String, replyTo: String? = null): Message =
        messages.sendText(convId, text, replyTo)
}
