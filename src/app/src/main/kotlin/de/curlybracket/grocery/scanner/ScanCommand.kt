package de.curlybracket.grocery.scanner

import de.curlybracket.grocery.domain.repository.GroceryRepository

sealed interface ScanCommand {
    suspend fun execute(repository: GroceryRepository, productId: String)

    data object DecrementStockCommand : ScanCommand {
        override suspend fun execute(repository: GroceryRepository, productId: String) {
            repository.decrementStock(productId)
        }
    }

    data object IncrementPendingStockCommand : ScanCommand {
        override suspend fun execute(repository: GroceryRepository, productId: String) {
            repository.incrementPendingStock(productId)
        }
    }
}

fun ScannerMode.toCommand(): ScanCommand = when (this) {
    is ScannerMode.DecrementStock -> ScanCommand.DecrementStockCommand
    is ScannerMode.IncrementPendingStock -> ScanCommand.IncrementPendingStockCommand
}
