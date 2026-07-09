package de.curlybracket.grocery.scanner

sealed class ScannerMode {
    abstract val householdId: String
    data class Inventory(override val householdId: String) : ScannerMode()
    data class Shopping(override val householdId: String) : ScannerMode()
}
