package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.data.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveContactsUseCase @Inject constructor(
    private val contacts: ContactRepository,
) {
    operator fun invoke(): Flow<List<Contact>> = contacts.observeAll()
}
