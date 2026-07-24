package de.curlybracket.grocery.scanner

import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScanCommandTest {

    private val repository: GroceryRepository = mockk(relaxed = true)
    private val productId = "p-1"

    @Test
    fun `DecrementStockCommand calls repository decrementStock`() = runTest {
        ScanCommand.DecrementStockCommand.execute(repository, productId)
        coVerify(exactly = 1) { repository.decrementStock(productId) }
    }

    @Test
    fun `IncrementPendingStockCommand calls repository incrementPendingStock`() = runTest {
        ScanCommand.IncrementPendingStockCommand.execute(repository, productId)
        coVerify(exactly = 1) { repository.incrementPendingStock(productId) }
    }

    @Test
    fun `DecrementStock mode converts to DecrementStockCommand`() {
        val mode = ScannerMode.DecrementStock("hh-1")
        val command = mode.toCommand()
        assert(command == ScanCommand.DecrementStockCommand)
    }

    @Test
    fun `IncrementPendingStock mode converts to IncrementPendingStockCommand`() {
        val mode = ScannerMode.IncrementPendingStock("hh-1")
        val command = mode.toCommand()
        assert(command == ScanCommand.IncrementPendingStockCommand)
    }
}
