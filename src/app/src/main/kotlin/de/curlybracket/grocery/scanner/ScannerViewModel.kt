package de.curlybracket.grocery.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.domain.repository.GroceryRepository
import de.curlybracket.grocery.network.OpenFoodFactsClient
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    val repository: GroceryRepository,
    val audioFeedback: AudioFeedback,
    val openFoodFactsClient: OpenFoodFactsClient,
) : ViewModel()
