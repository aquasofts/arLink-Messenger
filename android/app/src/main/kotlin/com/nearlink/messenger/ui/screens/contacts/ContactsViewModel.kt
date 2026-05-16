package com.nearlink.messenger.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearlink.messenger.core.model.AggregatedPresence
import com.nearlink.messenger.core.model.Contact
import com.nearlink.messenger.data.repository.PresenceRepository
import com.nearlink.messenger.domain.usecase.ObserveContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ContactsViewModel @Inject constructor(
    observeContacts: ObserveContactsUseCase,
    presenceRepository: PresenceRepository,
) : ViewModel() {
    val contacts: StateFlow<List<ContactListItem>> = observeContacts()
        .flatMapLatest { contacts ->
            if (contacts.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(contacts.map { contact ->
                    presenceRepository.observe(contact.deviceId)
                }) { presenceStates ->
                    contacts.mapIndexed { index, contact ->
                        ContactListItem(contact, presenceStates[index].aggregate())
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

data class ContactListItem(
    val contact: Contact,
    val presence: AggregatedPresence,
)
