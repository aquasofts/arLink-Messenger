package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.data.repository.MessageRepository
import javax.inject.Inject

class EditMessageUseCase @Inject constructor(
    private val messages: MessageRepository,
) {
    suspend operator fun invoke(messageId: String, newText: String) = messages.edit(messageId, newText)
}
