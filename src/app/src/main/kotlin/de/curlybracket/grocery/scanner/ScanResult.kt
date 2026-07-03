package de.curlybracket.grocery.scanner

import de.curlybracket.grocery.domain.model.ProductKind

sealed class ScanResult {
    data class Hit(val product: ProductKind) : ScanResult()
    data class Miss(val barcode: String) : ScanResult()
    data class Restored(val product: ProductKind) : ScanResult()
}
