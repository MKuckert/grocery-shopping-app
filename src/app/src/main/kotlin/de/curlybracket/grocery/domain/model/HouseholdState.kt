package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

enum class HouseholdState { IDLE, SHOPPING, UNLOADING }

@Immutable
data class Household(
  val id: String,
  val currentState: HouseholdState,
  val shoppingStartedAt: String?
) {
  companion object {
    fun fromMap(row: Map<String, Any?>): Household {
      return Household(
        id = row["id"] as String,
        currentState = HouseholdState.valueOf(row["current_state"] as String),
        shoppingStartedAt = row["shopping_started_at"] as? String
      )
    }
  }
}
