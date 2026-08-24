import dev.deftu.stateful.StateListener
import dev.deftu.stateful.Subscription
import dev.deftu.stateful.dsl.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import dev.deftu.stateful.dsl.subscribeOnce

class SubscriptionTest {
    @Test
    fun subscribeReceivesEveryChange() {
        val state = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        state.subscribe { value -> observed.add(value) }

        state.set(1)
        state.set(2)

        assertEquals(listOf(1, 2), observed)
    }

    @Test
    fun subscribeOnceReceivesOnlyTheFirstChange() {
        val state = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        state.subscribeOnce { value -> observed.add(value) }

        state.set(1)
        state.set(2)

        assertEquals(listOf(1), observed)
    }

    @Test
    fun subscribeOnceDisposesItselfAfterFiring() {
        val state = mutableStateOf(0)
        val subscription = state.subscribeOnce { }
        assertFalse(subscription.isDisposed)

        state.set(1)

        assertTrue(subscription.isDisposed)
    }

    @Test
    fun disposedSubscriptionStopsReceiving() {
        val state = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        val subscription = state.subscribe { value -> observed.add(value) }

        state.set(1)
        subscription.dispose()
        state.set(2)

        assertEquals(listOf(1), observed)
        assertTrue(subscription.isDisposed)
    }

    @Test
    fun disposingTwiceIsSafe() {
        val state = mutableStateOf(0)
        val subscription = state.subscribe { }

        subscription.dispose()
        subscription.dispose()

        assertTrue(subscription.isDisposed)
    }

    @Test
    fun identicalListenersDisposeIndependently() {
        val state = mutableStateOf(0)
        var count = 0
        val listener = StateListener<Int> { count++ }

        val first = state.subscribe(listener)
        state.subscribe(listener)

        first.dispose()
        state.set(1)

        assertEquals(1, count)
    }

    @Test
    fun settingAnIdenticalValueDoesNotNotify() {
        val state = mutableStateOf(0)
        var count = 0
        state.subscribe { count++ }

        state.set(0)

        assertEquals(0, count)
    }

    @Test
    fun subscribingDuringNotificationDoesNotFail() {
        val state = mutableStateOf(0)
        var added = 0
        state.subscribe {
            state.subscribe { added++ }
        }

        state.set(1)
        state.set(2)

        assertEquals(1, added)
    }

    @Test
    fun disposingDuringNotificationDoesNotFail() {
        val state = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        lateinit var subscription: Subscription
        subscription = state.subscribe { value ->
            observed.add(value)
            subscription.dispose()
        }

        state.set(1)
        state.set(2)

        assertEquals(listOf(1), observed)
    }

    @Test
    fun oneThrowingListenerDoesNotStarveTheOthers() {
        val state = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        state.subscribe { error("boom") }
        state.subscribe { value -> observed.add(value) }

        assertFailsWith<IllegalStateException> {
            state.set(1)
        }

        assertEquals(listOf(1), observed)
    }

    @Test
    fun updateAppliesTheTransform() {
        val state = mutableStateOf(2)

        state.update { it * 3 }

        assertEquals(6, state.value)
    }
}
