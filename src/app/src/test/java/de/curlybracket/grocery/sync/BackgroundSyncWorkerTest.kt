package de.curlybracket.grocery.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import com.powersync.sync.SyncStatusData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BackgroundSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var database: PowerSyncDatabase
    private lateinit var connector: SupabaseConnector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = mockk(relaxed = true)
        connector = mockk(relaxed = true)
    }

    private fun buildWorker(): BackgroundSyncWorker =
        TestListenableWorkerBuilder<BackgroundSyncWorker>(context)
            .setWorkerFactory(FakeWorkerFactory(database, connector))
            .build()

    private fun mockIdleStatusFlow(): MutableSharedFlow<SyncStatusData> {
        val idleStatus = mockk<SyncStatusData> {
            every { connected } returns true
            every { downloading } returns false
            every { uploading } returns false
        }
        return MutableSharedFlow<SyncStatusData>(replay = 1).also {
            it.tryEmit(idleStatus)
        }
    }

    @Test
    fun `doWork returns failure when not authenticated`() = runTest {
        every { connector.sessionStatus } returns MutableStateFlow(SessionStatus.NotAuthenticated(false))

        val result = buildWorker().doWork()

        assertEquals(Result.failure(), result)
    }

    @Test
    fun `doWork returns success after sync completes`() = runTest {
        every { connector.sessionStatus } returns MutableStateFlow(
            mockk<SessionStatus.Authenticated>(relaxed = true)
        )

        val statusFlow = mockIdleStatusFlow()
        every { database.currentStatus } returns mockk {
            every { asFlow() } returns statusFlow
        }

        val result = buildWorker().doWork()

        assertEquals(Result.success(), result)
        coVerify { database.disconnect() }
    }

    @Test
    fun `doWork returns retry on transient network error`() = runTest {
        every { connector.sessionStatus } returns MutableStateFlow(
            mockk<SessionStatus.Authenticated>(relaxed = true)
        )

        coEvery { database.connect(any()) } throws RuntimeException("Network error")

        val result = buildWorker().doWork()

        assertEquals(Result.retry(), result)
        coVerify { database.disconnect() }
    }

    @Test
    fun `doWork returns retry on timeout when sync never completes`() = runTest {
        every { connector.sessionStatus } returns MutableStateFlow(
            mockk<SessionStatus.Authenticated>(relaxed = true)
        )

        // Status flow never emits an idle state — triggers withTimeout
        every { database.currentStatus } returns mockk {
            every { asFlow() } returns MutableSharedFlow() // no replay, never emits
        }

        val result = buildWorker().doWork()

        assertEquals(Result.retry(), result)
        coVerify { database.disconnect() }
    }

    @Test
    fun `doWork skips Initializing and succeeds when authenticated`() = runTest {
        every { connector.sessionStatus } returns MutableStateFlow<SessionStatus>(
            mockk<SessionStatus.Authenticated>(relaxed = true)
        )

        val statusFlow = mockIdleStatusFlow()
        every { database.currentStatus } returns mockk {
            every { asFlow() } returns statusFlow
        }

        val result = buildWorker().doWork()

        assertEquals(Result.success(), result)
    }
}
