package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.data.repository.MessageRepository
import javax.inject.Inject

class RevokeMessageUseCase @Inject constructor(
    private val messages: MessageRepository,
) {
    suspend operator fun invoke(messageId: String) = messages.revoke(messageId)
}
