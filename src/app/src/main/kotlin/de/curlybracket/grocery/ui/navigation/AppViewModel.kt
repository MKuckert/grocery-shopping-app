package de.curlybracket.grocery.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.domain.model.Household
import de.curlybracket.grocery.domain.repository.GroceryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: GroceryRepository,
) : ViewModel() {
    val householdState: StateFlow<Household?> =
        repository.watchHousehold()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
