package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SnackbarMessage(
  val text: String,
  val productId: String,
  val actionLabel: String? = null
)
