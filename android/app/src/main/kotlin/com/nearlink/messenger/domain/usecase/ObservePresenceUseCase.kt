package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.model.PresenceState
import com.nearlink.messenger.data.repository.PresenceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePresenceUseCase @Inject constructor(
    private val presence: PresenceRepository,
) {
    operator fun invoke(deviceId: String): Flow<PresenceState> = presence.observe(deviceId)
}
