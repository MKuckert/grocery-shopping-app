package de.curlybracket.grocery.scanner

import app.cash.turbine.test
import de.curlybracket.grocery.audio.AudioFeedback
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import de.curlybracket.grocery.network.OFResult
import de.curlybracket.grocery.network.OpenFoodFactsClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ScannerProcessorTest {

    private lateinit var repository: GroceryRepository
    private lateinit var audioFeedback: AudioFeedback
    private lateinit var openFoodFactsClient: OpenFoodFactsClient
    private lateinit var processor: ScannerProcessor

    private val householdId = "hh-1"
    private val barcode = "1234567890"
    private val mode = ScannerMode.Inventory(householdId)

    private fun makeProduct(
        id: String = "p-1",
        name: String = "Milk",
        deletedAt: String? = null,
    ) = ProductKind(
        id = id,
        householdId = householdId,
        groupId = "g-1",
        name = name,
        currentStock = 2,
        minimumStock = 1,
        quantityToBuy = 0,
        pendingStock = 0,
        imagePath = null,
        unloadOpen = false,
        deletedAt = deletedAt,
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        audioFeedback = mockk(relaxed = true)
        openFoodFactsClient = mockk(relaxed = true)
        processor = ScannerProcessor(repository, audioFeedback, openFoodFactsClient)
    }

    @Test
    fun `processScan with known barcode emits Hit and calls mode-specific mutation`() = runTest {
        val product = makeProduct()
        coEvery { repository.findByBarcode(barcode, householdId) } returns product

        processor.scanResultFlow.test {
            processor.processScan(barcode, mode)
            val result = awaitItem()
            assertEquals(ScanResult.Hit(product), result)
        }

        coVerify(exactly = 1) { repository.decrementStock(product.id) }
        coVerify(exactly = 0) { repository.incrementPendingStock(any()) }
    }

    @Test
    fun `processScan Shopping mode calls incrementPendingStock`() = runTest {
        val product = makeProduct()
        val shoppingMode = ScannerMode.Shopping(householdId)
        coEvery { repository.findByBarcode(barcode, householdId) } returns product

        processor.scanResultFlow.test {
            processor.processScan(barcode, shoppingMode)
            awaitItem()
        }

        coVerify(exactly = 1) { repository.incrementPendingStock(product.id) }
        coVerify(exactly = 0) { repository.decrementStock(any()) }
    }

    @Test
    fun `processScan with unknown barcode emits Miss and triggers OpenFoodFacts lookup`() = runTest {
        coEvery { repository.findByBarcode(barcode, householdId) } returns null
        coEvery { openFoodFactsClient.lookupBarcode(barcode) } returns OFResult.Hit("Best Milk")

        processor.scanResultFlow.test {
            processor.openFoodFactsResultFlow.test {
                processor.processScan(barcode, mode)

                val offResult = awaitItem()
                assertEquals(barcode, offResult.barcode)
                assertEquals("Best Milk", offResult.prefillName)
            }
            val scanResult = awaitItem()
            assert(scanResult is ScanResult.Miss)
            assertEquals(barcode, (scanResult as ScanResult.Miss).barcode)
        }
    }

    @Test
    fun `processScan with soft-deleted product emits Restored and calls restoreProductKind`() = runTest {
        val deletedProduct = makeProduct(deletedAt = "2024-01-01")
        coEvery { repository.findByBarcode(barcode, householdId) } returns deletedProduct

        processor.scanResultFlow.test {
            processor.processScan(barcode, mode)
            val result = awaitItem()
            assert(result is ScanResult.Restored)
            assertNull((result as ScanResult.Restored).product.deletedAt)
        }

        coVerify(exactly = 1) { repository.restoreProductKind(deletedProduct.id) }
        coVerify(exactly = 1) { repository.recalculateQuantityToBuy(deletedProduct.id) }
    }

    @Test
    fun `processScan when findByBarcode throws does not crash and plays failure audio`() = runTest {
        coEvery { repository.findByBarcode(barcode, householdId) } throws RuntimeException("DB error")

        // Should not throw
        processor.processScan(barcode, mode)

        coVerify(exactly = 1) { audioFeedback.playFailure() }
    }

    @Test
    fun `processScan plays success audio on Hit`() = runTest {
        val product = makeProduct()
        coEvery { repository.findByBarcode(barcode, householdId) } returns product

        processor.scanResultFlow.test {
            processor.processScan(barcode, mode)
            awaitItem()
        }

        coVerify(exactly = 1) { audioFeedback.playSuccess() }
    }

    @Test
    fun `processScan plays success audio on Restored`() = runTest {
        val deletedProduct = makeProduct(deletedAt = "2024-01-01")
        coEvery { repository.findByBarcode(barcode, householdId) } returns deletedProduct

        processor.scanResultFlow.test {
            processor.processScan(barcode, mode)
            awaitItem()
        }

        coVerify(exactly = 1) { audioFeedback.playSuccess() }
    }

    @Test
    fun `processScan plays failure audio on Miss`() = runTest {
        coEvery { repository.findByBarcode(barcode, householdId) } returns null
        coEvery { openFoodFactsClient.lookupBarcode(barcode) } returns OFResult.Miss

        processor.scanResultFlow.test {
            processor.processScan(barcode, mode)
            awaitItem()
        }

        coVerify(exactly = 1) { audioFeedback.playFailure() }
    }

    @Test
    fun `createNewProduct creates product and returns it`() = runTest {
        val product = makeProduct()
        coEvery { repository.ensureUnsortedGroup(householdId) } returns "g-1"
        coEvery { repository.createProductKind(any(), any(), any(), any(), any()) } returns product.id
        coEvery { repository.findByBarcode(barcode, householdId) } returns product

        val result = processor.createNewProduct(
            context = mockk(relaxed = true),
            barcode = barcode,
            productName = "Milk",
            householdId = householdId,
            photoPath = null,
        )

        assertEquals(product, result)
        coVerify(exactly = 1) { repository.createProductKind(householdId, "Milk", "g-1", 1, barcode) }
    }

    @Test
    fun `createNewProduct handles photo move failure gracefully and still returns product`() = runTest {
        val product = makeProduct()
        coEvery { repository.ensureUnsortedGroup(householdId) } returns "g-1"
        coEvery { repository.createProductKind(any(), any(), any(), any(), any()) } returns product.id
        coEvery { repository.findByBarcode(barcode, householdId) } returns product

        // Provide a non-existent photo path — the file copy will fail
        val result = processor.createNewProduct(
            context = mockk(relaxed = true),
            barcode = barcode,
            productName = "Milk",
            householdId = householdId,
            photoPath = "/nonexistent/path/photo.jpg",
        )

        // Product is still returned despite photo failure
        assertEquals(product, result)
        // updateProductKind should NOT be called when photo move fails
        coVerify(exactly = 0) { repository.updateProductKind(any(), any(), any(), any(), any(), any()) }
    }
}
