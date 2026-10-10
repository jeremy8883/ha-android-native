package io.homeassistant.companion.android.dashboard.data

import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.websocket.WebSocketConnectionStatus
import io.homeassistant.companion.android.common.data.websocket.WebSocketState
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KeptDataTest {

    private class FakeKeeper<T>(var kept: Kept<T>? = null) : ValueKeeper<T> {
        override suspend fun get(): Kept<T>? = kept

        override fun put(value: T) {
            kept = Kept(value, KEPT_AT)
        }
    }

    private val connection = MutableStateFlow(WebSocketConnectionStatus(WebSocketState.Active, connections = 1))
    private val retryDelays = RetryDelays { 1.seconds }

    private fun reconnect() {
        connection.value = connection.value.withState(WebSocketState.ClosedOther).withState(WebSocketState.Active)
    }

    @Nested
    inner class Fetches {
        @Test
        fun `Given nothing loaded when the fetch fails then it fails, and is retried until it loads`() = runTest {
            val results = ArrayDeque(listOf(Fetched.Failure(LoadError.NoResponse), Fetched.Success("areas")))
            KeptData("test", FakeKeeper<String>(), connection, retryDelays).fetched { results.removeFirst() }
                .test {
                    assertEquals(Loadable.Loading, awaitItem())
                    assertEquals(Loadable.Failed(LoadError.NoResponse), awaitItem())
                    advanceTimeBy(1.seconds + 1.seconds / 10)
                    assertEquals(Loadable.Ready("areas"), awaitItem())
                }
        }

        @Test
        fun `Given a loaded value when loading it again fails then the value stays, with the error`() = runTest {
            val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
            val error = LoadError.Server(code = "unknown_error", message = "Boom")
            val results = ArrayDeque(listOf(Fetched.Success("areas"), Fetched.Failure(error)))
            KeptData("test", FakeKeeper<String>(), connection, retryDelays).fetched(refreshes) { results.removeFirst() }.test {
                assertEquals(Loadable.Loading, awaitItem())
                assertEquals(Loadable.Ready("areas"), awaitItem())
                refreshes.emit(Unit)
                assertEquals(Loadable.Ready("areas", refreshing = true), awaitItem())
                assertEquals(Loadable.Ready("areas", refreshError = error), awaitItem())
                // The server refused, so it isn't retried on its own
                advanceTimeBy(1.seconds * 60)
                expectNoEvents()
            }
        }

        @Test
        fun `Given a kept value when collected then it shows while refreshing, then the loaded one`() = runTest {
            val keeper = FakeKeeper(Kept("old areas", KEPT_AT))
            KeptData("test", keeper, connection).fetched { Fetched.Success("new areas") }.test {
                assertEquals(Loadable.Ready("old areas", refreshing = true, keptAt = KEPT_AT), awaitItem())
                assertEquals(Loadable.Ready("new areas"), awaitItem())
            }
            assertEquals(Kept("new areas", KEPT_AT), keeper.kept)
        }

        @Test
        fun `Given a loaded value when the connection is established again then it is loaded again`() = runTest {
            var fetches = 0
            KeptData("test", FakeKeeper<Int>(), connection).fetched { Fetched.Success(++fetches) }.test {
                assertEquals(Loadable.Loading, awaitItem())
                assertEquals(Loadable.Ready(1), awaitItem())
                reconnect()
                assertEquals(Loadable.Ready(1, refreshing = true), awaitItem())
                assertEquals(Loadable.Ready(2), awaitItem())
            }
        }

        @Test
        fun `Given no server when collected then it fails without a server`() = runTest {
            KeptData("test", FakeKeeper<String>(), connection = null).fetched {
                Fetched.Failure(LoadError.NoServer)
            }.test {
                assertEquals(Loadable.Loading, awaitItem())
                assertEquals(Loadable.Failed(LoadError.NoServer), awaitItem())
                expectNoEvents()
            }
        }
    }

    @Nested
    inner class Subscriptions {
        private fun sum(events: MutableSharedFlow<Int>, keeper: ValueKeeper<List<Int>> = FakeKeeper()) = KeptData("test", keeper, connection, retryDelays).subscribed(
            subscribe = { Fetched.Success(events) },
            reduce = { current, event -> current.orEmpty() + event },
        )

        @Test
        fun `Given events when subscribed then each one is applied to the value`() = runTest {
            val events = MutableSharedFlow<Int>()
            sum(events).test {
                assertEquals(Loadable.Loading, awaitItem())
                events.emit(1)
                assertEquals(Loadable.Ready(listOf(1)), awaitItem())
                events.emit(2)
                assertEquals(Loadable.Ready(listOf(1, 2)), awaitItem())
            }
        }

        @Test
        fun `Given a reconnection when the next event comes then it is a new snapshot`() = runTest {
            val events = MutableSharedFlow<Int>()
            sum(events).test {
                assertEquals(Loadable.Loading, awaitItem())
                events.emit(1)
                assertEquals(Loadable.Ready(listOf(1)), awaitItem())
                reconnect()
                assertEquals(Loadable.Ready(listOf(1), refreshing = true), awaitItem())
                // What was removed while disconnected is gone from the new snapshot
                events.emit(2)
                assertEquals(Loadable.Ready(listOf(2)), awaitItem())
            }
        }

        @Test
        fun `Given a kept value when subscribing fails then the value stays, and it subscribes again`() = runTest {
            var attempts = 0
            KeptData("test", FakeKeeper(Kept(listOf(1), KEPT_AT)), connection, retryDelays).subscribed<Int>(
                subscribe = {
                    if (attempts++ == 0) Fetched.Failure(LoadError.NoResponse) else Fetched.Success(flowOf(5))
                },
                reduce = { current, event -> current.orEmpty() + event },
            ).test {
                assertEquals(Loadable.Ready(listOf(1), refreshing = true, keptAt = KEPT_AT), awaitItem())
                assertEquals(
                    Loadable.Ready(listOf(1), refreshError = LoadError.NoResponse, keptAt = KEPT_AT),
                    awaitItem(),
                )
                advanceTimeBy(1.seconds + 1.seconds / 10)
                assertEquals(Loadable.Ready(listOf(5)), awaitItem())
                // The subscription ended, which is a failure too
                assertEquals(Loadable.Ready(listOf(5), refreshError = LoadError.NoResponse), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given a subscription without its first message when changes come then it subscribes again for one`() = runTest {
            var subscriptions = 0
            val late = MutableSharedFlow<String>()
            val fresh = MutableSharedFlow<String>()
            KeptData<List<String>>("test", FakeKeeper(), connection, retryDelays).subscribed(
                subscribe = { Fetched.Success(if (subscriptions++ == 0) late else fresh) },
                // As the entity states do: changes need a snapshot to apply to
                reduce = { current, event ->
                    when {
                        event.startsWith("snapshot") -> listOf(event)
                        current != null -> current + event
                        else -> null
                    }
                },
            ).test {
                assertEquals(Loadable.Loading, awaitItem())
                // A subscription joined after its snapshot was sent: never shown as an empty value
                repeat(3) { late.emit("change") }
                assertEquals(Loadable.Failed(LoadError.NoResponse), awaitItem())
                advanceTimeBy(1.seconds + 1.seconds / 10)
                fresh.emit("snapshot")
                assertEquals(Loadable.Ready(listOf("snapshot")), awaitItem())
                fresh.emit("change")
                assertEquals(Loadable.Ready(listOf("snapshot", "change")), awaitItem())
                assertEquals(2, subscriptions)
            }
        }
    }

    @Nested
    inner class Combining {
        @Test
        fun `Given a failure and a loading value when combined then it is the failure`() {
            val combined = combineLoadables(Loadable.Failed(LoadError.NoResponse), Loadable.Loading) { a: Int, b: Int ->
                a + b
            }
            assertEquals(Loadable.Failed(LoadError.NoResponse), combined)
        }

        @Test
        fun `Given ready values when combined then refreshing and errors carry over`() {
            val combined = combineLoadables(
                Loadable.Ready(1, refreshing = true),
                Loadable.Ready(2, refreshError = LoadError.NoResponse),
            ) { a, b -> a + b }
            assertEquals(Loadable.Ready(3, refreshing = true, refreshError = LoadError.NoResponse), combined)
        }
    }

    private companion object {
        val KEPT_AT = Instant.fromEpochSeconds(1_700_000_000)
    }
}
