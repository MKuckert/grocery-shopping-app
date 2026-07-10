package de.curlybracket.grocery.scanner

sealed class ScannerMode {
    abstract val householdId: String
    data class DecrementStock(override val householdId: String) : ScannerMode()
    data class IncrementPendingStock(override val householdId: String) : ScannerMode()
}
