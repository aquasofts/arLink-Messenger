package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.model.Conversation
import com.nearlink.messenger.data.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveConversationsUseCase @Inject constructor(
    private val convs: ConversationRepository,
) {
    operator fun invoke(): Flow<List<Conversation>> = convs.observeAll()
}
