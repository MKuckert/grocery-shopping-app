package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

enum class HouseholdState { IDLE, SHOPPING, UNLOADING }

@Immutable
data class Household(
    val id: String,
    val currentState: HouseholdState,
    val shoppingStartedAt: String?,
)
