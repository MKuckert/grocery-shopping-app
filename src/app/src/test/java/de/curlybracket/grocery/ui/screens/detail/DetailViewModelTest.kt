package de.curlybracket.grocery.ui.screens.detail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import de.curlybracket.grocery.domain.model.ProductKind
import de.curlybracket.grocery.domain.repository.GroceryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: GroceryRepository
    private lateinit var context: Context
    private val productId = "p-1"
    private val householdId = "hh-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns ""
        every { context.getString(any(), *anyVararg()) } returns ""
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeProduct(name: String = "Milk") = ProductKind(
        id = productId,
        householdId = householdId,
        groupId = "g-1",
        name = name,
        currentStock = 2,
        minimumStock = 1,
        quantityToBuy = 1,
        pendingStock = 0,
        imagePath = null,
        unloadOpen = false,
        deletedAt = null,
    )

    private fun makeViewModel(
        productFlow: MutableStateFlow<ProductKind?> = MutableStateFlow<ProductKind?>(makeProduct()),
    ): DetailViewModel {
        coEvery { repository.watchProductKind(productId) } returns productFlow
        coEvery { repository.watchProductGroups() } returns MutableStateFlow(emptyList())
        coEvery { repository.watchBarcodes(productId) } returns MutableStateFlow(emptyList())
        return DetailViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("productId" to productId)),
            context = context,
        )
    }

    // -------------------------------------------------------------------------
    // Auto-save debounce — changes trigger save after 800ms of inactivity
    // -------------------------------------------------------------------------

    @Test
    fun `auto-save triggers updateProductKind after 800ms of inactivity`() = runTest {
        val viewModel = makeViewModel()
        // Let init{} coroutines start so the debounce collector is active
        runCurrent()

        viewModel.updateName("Updated Name")
        // Propagate the StateFlow emission through the combine operator to debounce
        runCurrent()

        // Before 800ms — should NOT have saved yet
        advanceTimeBy(799)
        coVerify(exactly = 0) { repository.updateProductKind(any(), any(), any(), any(), any(), any()) }

        // After 800ms — debounce fires, save should be called
        advanceTimeBy(1)
        // Run the performSave coroutine that was scheduled by the debounce
        runCurrent()
        coVerify(exactly = 1) {
            repository.updateProductKind(
                productId = productId,
                name = "Updated Name",
                groupId = any(),
                minimumStock = any(),
                currentStock = any(),
                imagePath = any(),
            )
        }
    }

    @Test
    fun `rapid edits only trigger one save after final edit`() = runTest {
        val viewModel = makeViewModel()
        runCurrent()

        // Rapidly update name — debounce resets on each update; only one save expected
        viewModel.updateName("A")
        runCurrent()
        advanceTimeBy(400)
        viewModel.updateName("AB")
        runCurrent()
        advanceTimeBy(400)
        viewModel.updateName("ABC")
        runCurrent()
        advanceTimeBy(400) // only 400ms past last edit — debounce hasn't fired

        coVerify(exactly = 0) { repository.updateProductKind(any(), any(), any(), any(), any(), any()) }

        // Let debounce expire (remaining 400ms of the 800ms window)
        advanceTimeBy(400)
        runCurrent()
        coVerify(exactly = 1) {
            repository.updateProductKind(
                productId = productId,
                name = "ABC",
                groupId = any(),
                minimumStock = any(),
                currentStock = any(),
                imagePath = any(),
            )
        }
    }

    @Test
    fun `initial product load does NOT trigger a save`() = runTest {
        makeViewModel()
        // Advance past any debounce window without user edits
        advanceTimeBy(2_000)

        coVerify(exactly = 0) { repository.updateProductKind(any(), any(), any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // onCleared() — immediate save of pending changes
    // -------------------------------------------------------------------------

    @Test
    fun `onCleared triggers immediate save when user has pending edits`() = runTest {
        val viewModel = makeViewModel()
        runCurrent()

        viewModel.updateName("Pending Edit")
        runCurrent()
        // Only 300ms have passed — debounce still 500ms away
        advanceTimeBy(300)

        coVerify(exactly = 0) { repository.updateProductKind(any(), any(), any(), any(), any(), any()) }

        // Clearing the ViewModel should trigger an immediate blocking save
        viewModel.onCleared()

        coVerify(exactly = 1) {
            repository.updateProductKind(
                productId = productId,
                name = "Pending Edit",
                groupId = any(),
                minimumStock = any(),
                currentStock = any(),
                imagePath = any(),
            )
        }
    }

    @Test
    fun `onCleared does NOT save when no pending edits`() = runTest {
        val viewModel = makeViewModel()
        runCurrent()

        // No edits made — _userEditing remains false
        viewModel.onCleared()

        coVerify(exactly = 0) { repository.updateProductKind(any(), any(), any(), any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // savedIndicator — briefly shows after auto-save, then resets
    // -------------------------------------------------------------------------

    @Test
    fun `savedIndicator becomes true briefly after auto-save and then resets`() = runTest {
        val viewModel = makeViewModel()
        runCurrent()

        assertFalse("Indicator should be off initially", viewModel.savedIndicator.value)

        viewModel.updateName("New Name")
        runCurrent()
        advanceTimeBy(800) // debounce fires, performSave runs, sets _savedIndicator = true
        runCurrent()

        assertTrue("Indicator should be on after save", viewModel.savedIndicator.value)

        // After performSave's delay(1_500) expires, indicator resets
        advanceTimeBy(1_500)
        runCurrent()
        assertFalse("Indicator should be off after 1.5s", viewModel.savedIndicator.value)
    }
}
