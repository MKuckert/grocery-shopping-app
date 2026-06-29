package de.curlybracket.grocery.scanner

sealed class ScannerMode {
  data class Inventory(val householdId: String) : ScannerMode()
  data class Shopping(val householdId: String) : ScannerMode()
}
