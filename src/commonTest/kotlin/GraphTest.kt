import dev.deftu.stateful.Equality
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphTest {
    @Test
    fun anEffectOverADiamondFiresOnceWithTheSettledValue() {
        val root = mutableStateOf(1)
        val left = memo { root() * 2 }
        val right = memo { root() * 3 }
        val sum = memo { left() + right() }

        val observed = mutableListOf<Int>()
        effect { observed.add(sum()) }
        observed.clear()

        root.set(2)

        assertEquals(listOf(10), observed)
    }

    @Test
    fun anUnobservedMemoNeverComputes() {
        var computations = 0
        val source = mutableStateOf(1)
        memo {
            computations++
            source() * 2
        }

        source.set(2)
        source.set(3)

        assertEquals(0, computations)
    }

    @Test
    fun aMemoRecomputesOncePerObservedChange() {
        var computations = 0
        val source = mutableStateOf(1)
        val doubled = memo {
            computations++
            source() * 2
        }

        assertEquals(2, doubled.value)
        assertEquals(2, doubled.value)
        assertEquals(1, computations)

        source.set(2)
        assertEquals(4, doubled.value)
        assertEquals(2, computations)
    }

    @Test
    fun aMemoWhoseValueIsUnchangedDoesNotWakeItsDependents() {
        var downstream = 0
        val source = mutableStateOf(1)
        val parity = memo { source() % 2 }
        val derived = memo {
            downstream++
            parity()
        }

        assertEquals(1, derived.value)
        assertEquals(1, downstream)

        source.set(3)

        assertEquals(1, derived.value)
        assertEquals(1, downstream)
    }

    @Test
    fun anUntakenBranchDoesNotSubscribe() {
        val condition = mutableStateOf(true)
        val left = mutableStateOf("left")
        val right = mutableStateOf("right")

        var runs = 0
        val chosen = memo {
            runs++
            if (condition()) left() else right()
        }

        assertEquals("left", chosen.value)
        assertEquals(1, runs)

        right.set("changed")
        assertEquals("left", chosen.value)
        assertEquals(1, runs)

        condition.set(false)
        assertEquals("changed", chosen.value)
        assertEquals(2, runs)

        left.set("no longer observed")
        assertEquals("changed", chosen.value)
        assertEquals(2, runs)
    }

    @Test
    fun aResubscribedBranchIsTrackedAgain() {
        val condition = mutableStateOf(true)
        val left = mutableStateOf(1)
        val right = mutableStateOf(100)
        val chosen = memo { if (condition()) left() else right() }

        assertEquals(1, chosen.value)

        condition.set(false)
        assertEquals(100, chosen.value)

        condition.set(true)
        left.set(2)

        assertEquals(2, chosen.value)
    }

    @Test
    fun disposingASubscriptionInsideItsOwnListenerDoesNotCorruptDispatch() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        lateinit var subscription: dev.deftu.stateful.Subscription
        subscription = source.subscribe { value ->
            observed.add(value)
            subscription.dispose()
        }
        source.subscribe { value -> observed.add(value * 100) }

        source.set(1)
        source.set(2)

        assertTrue(subscription.isDisposed)
        assertEquals(listOf(1, 100, 200), observed)
    }

    @Test
    fun customEqualitySuppressesPropagation() {
        val source = mutableStateOf(1) { a, b -> a % 2 == b % 2 }
        val observed = mutableListOf<Int>()
        source.subscribe { value -> observed.add(value) }

        source.set(3)
        source.set(5)
        source.set(2)

        assertEquals(listOf(2), observed)
    }

    @Test
    fun neverEqualAlwaysPropagates() {
        val source = mutableStateOf(1, Equality.never())
        val observed = mutableListOf<Int>()
        source.subscribe { value -> observed.add(value) }

        source.set(1)
        source.set(1)

        assertEquals(listOf(1, 1), observed)
    }

    @Test
    fun structuralEqualityStopsARepeatedWrite() {
        val source = mutableStateOf(1)
        val observed = mutableListOf<Int>()
        source.subscribe { value -> observed.add(value) }

        source.set(1)
        source.set(2)
        source.set(2)

        assertEquals(listOf(2), observed)
    }

    @Test
    fun anEffectSeesEveryDependencyItReads() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(10)
        val observed = mutableListOf<Int>()

        effect { observed.add(first() + second()) }

        first.set(2)
        second.set(20)

        assertEquals(listOf(11, 12, 22), observed)
    }

    @Test
    fun aDisposedEffectStopsRunning() {
        val source = mutableStateOf(1)
        val observed = mutableListOf<Int>()
        val handle = effect { observed.add(source()) }

        source.set(2)
        handle.dispose()
        source.set(3)

        assertTrue(handle.isDisposed)
        assertEquals(listOf(1, 2), observed)
    }
}
