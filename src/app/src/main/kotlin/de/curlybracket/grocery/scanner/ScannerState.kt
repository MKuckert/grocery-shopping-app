package de.curlybracket.grocery.scanner

sealed class ScannerState {
    data object Scanning : ScannerState()

    data class CaptureRequired(
        val barcode: String,
        val prefillName: String,
        val photoPath: String?,
    ) : ScannerState()

    data class LinkToExisting(val barcode: String) : ScannerState()
}
