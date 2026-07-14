package de.curlybracket.grocery.scanner

sealed interface LinkError {
    data object AlreadyLinked : LinkError
    data object GenericFailure : LinkError
}

class BarcodeAlreadyLinkedException(val barcode: String) :
    Exception("Barcode $barcode is already linked to a product")
