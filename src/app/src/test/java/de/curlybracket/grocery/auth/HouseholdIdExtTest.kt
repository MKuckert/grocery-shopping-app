package de.curlybracket.grocery.auth

import com.powersync.connector.supabase.SupabaseConnector
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdIdExtTest {

    private fun connector(flow: MutableStateFlow<SessionStatus>): SupabaseConnector =
        mockk<SupabaseConnector>().also { every { it.sessionStatus } returns flow }

    private fun authenticatedStatus(user: UserInfo?): SessionStatus.Authenticated {
        val session = mockk<UserSession>(relaxed = true)
        every { session.user } returns user
        return mockk<SessionStatus.Authenticated>(relaxed = true).also {
            every { it.session } returns session
        }
    }

    @Test
    fun `authenticated session with valid household_id returns the ID`() = runTest {
        val flow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        // backgroundScope is cancelled automatically at test end — avoids UncompletedCoroutinesError
        val resultFlow = connector(flow).householdIdFlow(backgroundScope)

        val user = mockk<UserInfo>(relaxed = true)
        every { user.appMetadata } returns JsonObject(mapOf("household_id" to JsonPrimitive("hh-99")))
        flow.value = authenticatedStatus(user)

        val result = resultFlow.first { it != null }
        assertEquals("hh-99", result)
    }

    @Test
    fun `authenticated session without household_id returns null`() = runTest {
        val flow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val resultFlow = connector(flow).householdIdFlow(backgroundScope)

        val user = mockk<UserInfo>(relaxed = true)
        every { user.appMetadata } returns JsonObject(emptyMap())
        flow.value = authenticatedStatus(user)

        val result = resultFlow.first()
        assertNull(result)
    }

    @Test
    fun `NotAuthenticated session returns null`() = runTest {
        val flow = MutableStateFlow<SessionStatus>(SessionStatus.NotAuthenticated(false))
        val resultFlow = connector(flow).householdIdFlow(backgroundScope)

        val result = resultFlow.first()
        assertNull(result)
    }

    @Test
    fun `Initializing session returns null`() = runTest {
        val flow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val resultFlow = connector(flow).householdIdFlow(backgroundScope)

        val result = resultFlow.first()
        assertNull(result)
    }

    @Test
    fun `authenticated session with null user returns null`() = runTest {
        val flow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
        val resultFlow = connector(flow).householdIdFlow(backgroundScope)

        flow.value = authenticatedStatus(user = null)

        val result = resultFlow.first()
        assertNull(result)
    }
}
