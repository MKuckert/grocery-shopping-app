package de.curlybracket.grocery.data.repository

import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.db.internal.PowerSyncTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GroceryRepositoryImplTest {

    private lateinit var db: PowerSyncDatabase
    private lateinit var connector: SupabaseConnector
    private lateinit var repository: GroceryRepositoryImpl

    @Before
    fun setUp() {
        db = mockk(relaxed = true)
        connector = mockk(relaxed = true)
        repository = GroceryRepositoryImpl(db)
    }

    /**
     * Sets up writeTransaction to invoke the callback with a mock transaction,
     * enabling verification of the SQL statements sent within the transaction.
     */
    @Suppress("UNCHECKED_CAST")
    private fun captureAndRunTransaction(tx: PowerSyncTransaction = mockk(relaxed = true)) {
        coEvery { db.writeTransaction<Any?>(any()) } coAnswers {
            val callback = invocation.args[0]
            // ThrowableTransactionCallback is a fun interface with execute(tx) -> R
            val executeMethod = callback!!.javaClass.methods.first { it.name == "execute" }
            executeMethod.invoke(callback, tx)
        }
    }

    // -------------------------------------------------------------------------
    // decrementStock — verifies the MAX(0) guard SQL is used
    // -------------------------------------------------------------------------

    @Test
    fun `decrementStock uses MAX(0) guard in SQL to prevent negative stock`() = runTest {
        val tx = mockk<PowerSyncTransaction>(relaxed = true)
        captureAndRunTransaction(tx)

        repository.decrementStock("p-1")

        coVerify {
            tx.execute(
                "UPDATE product_kinds SET current_stock = MAX(0, current_stock - 1) WHERE id = ?",
                listOf("p-1"),
            )
        }
    }

    @Test
    fun `decrementStock recalculates quantity_to_buy in the same transaction`() = runTest {
        val tx = mockk<PowerSyncTransaction>(relaxed = true)
        captureAndRunTransaction(tx)

        repository.decrementStock("p-1")

        coVerify {
            tx.execute(
                "UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?",
                listOf("p-1"),
            )
        }
    }

    // -------------------------------------------------------------------------
    // submitUnloading — verifies atomic state reset
    // -------------------------------------------------------------------------

    @Test
    fun `submitUnloading applies pending stock to current stock`() = runTest {
        val tx = mockk<PowerSyncTransaction>(relaxed = true)
        captureAndRunTransaction(tx)

        repository.submitUnloading("hh-1")

        coVerify {
            tx.execute(
                match { sql -> sql.contains("current_stock = current_stock + pending_stock") },
                listOf("hh-1"),
            )
        }
    }

    @Test
    fun `submitUnloading resets household state to IDLE`() = runTest {
        val tx = mockk<PowerSyncTransaction>(relaxed = true)
        captureAndRunTransaction(tx)

        repository.submitUnloading("hh-1")

        coVerify {
            tx.execute(
                "UPDATE households SET current_state = 'IDLE' WHERE id = (SELECT id FROM households LIMIT 1)",
            )
        }
    }

    // -------------------------------------------------------------------------
    // recalculateQuantityToBuy — non-transactional, verifies MAX(0) formula
    // -------------------------------------------------------------------------

    @Test
    fun `recalculateQuantityToBuy uses MAX(0, minimumStock - currentStock) formula`() = runTest {
        repository.recalculateQuantityToBuy("p-42")

        coVerify {
            db.execute(
                "UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?",
                listOf("p-42"),
            )
        }
    }

    // -------------------------------------------------------------------------
    // watchProductKind — soft-delete filter
    // -------------------------------------------------------------------------

    @Test
    fun `watchProductKind SQL includes deleted_at IS NULL filter`() {
        val capturedSql = mutableListOf<String>()
        every { db.watch(capture(capturedSql), any(), any()) } returns emptyFlow()

        repository.watchProductKind("p-deleted")

        assert(capturedSql.any { it.contains("deleted_at IS NULL") }) {
            "Expected SQL to contain 'deleted_at IS NULL' but got: ${capturedSql.firstOrNull()}"
        }
    }

    // -------------------------------------------------------------------------
    // findByBarcode — soft-delete filter
    // -------------------------------------------------------------------------

    @Test
    fun `findByBarcode SQL includes deleted_at IS NULL filter`() = runTest {
        val capturedSql = mutableListOf<String>()
        coEvery { db.getOptional(capture(capturedSql), any(), any()) } returns null

        repository.findByBarcode(barcodeNumber = "1234", householdId = "hh-1")

        assert(capturedSql.any { it.contains("pk.deleted_at IS NULL") }) {
            "Expected SQL to contain 'pk.deleted_at IS NULL' but got: ${capturedSql.firstOrNull()}"
        }
    }

    @Test
    fun `findByBarcode returns null when product is deleted`() = runTest {
        coEvery { db.getOptional(any(), any(), any()) } returns null

        val result = repository.findByBarcode(barcodeNumber = "1234", householdId = "hh-1")

        assertNull(result)
    }

    // -------------------------------------------------------------------------
    // createProductKind — verifies both inserts inside transaction
    // -------------------------------------------------------------------------

    @Test
    fun `createProductKind executes product and barcode inserts in a single transaction`() = runTest {
        val executedSqls = mutableListOf<String>()
        val tx = mockk<PowerSyncTransaction>(relaxed = true)
        every { tx.execute(capture(executedSqls), any()) } returns 1L
        captureAndRunTransaction(tx)

        repository.createProductKindWithBarcode(
            householdId = "hh-1",
            name = "Milk",
            groupId = "g-1",
            minimumStock = 2,
            barcodeNumber = "1234",
        )

        assertEquals(2, executedSqls.size)
        assert(executedSqls[0].contains("INSERT INTO product_kinds"))
        assert(executedSqls[1].contains("INSERT INTO barcodes"))
    }

    @Test
    fun `createProductKind returns a non-null non-blank product ID`() = runTest {
        captureAndRunTransaction()

        val productId = repository.createProductKindWithBarcode(
            householdId = "hh-1",
            name = "Bread",
            groupId = "g-1",
            minimumStock = 1,
            barcodeNumber = "9876",
        )

        assertNotNull(productId)
        assert(productId.isNotBlank())
    }

    @Test
    fun `createProductKind sets quantity_to_buy equal to minimumStock on creation`() = runTest {
        val capturedParams = mutableListOf<List<Any?>>()
        val tx = mockk<PowerSyncTransaction>(relaxed = true)
        every { tx.execute(any(), capture(capturedParams)) } returns 1L
        captureAndRunTransaction(tx)

        repository.createProductKindWithBarcode(
            householdId = "hh-42",
            name = "Yogurt",
            groupId = "g-7",
            minimumStock = 3,
            barcodeNumber = "5555",
        )

        // params[0] = product_kinds INSERT: id, householdId, groupId, name, minimumStock, quantityToBuy
        val productParams = capturedParams[0]
        assertEquals("hh-42", productParams[1])
        assertEquals("g-7", productParams[2])
        assertEquals("Yogurt", productParams[3])
        assertEquals(3, productParams[4])
        // quantity_to_buy == minimumStock on creation (current_stock = 0)
        assertEquals(3, productParams[5])
    }
}
