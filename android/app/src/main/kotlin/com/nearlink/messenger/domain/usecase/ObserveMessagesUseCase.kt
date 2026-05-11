package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.model.Message
import com.nearlink.messenger.data.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(
    private val messages: MessageRepository,
) {
    operator fun invoke(convId: String): Flow<List<Message>> = messages.observeConv(convId)
}
