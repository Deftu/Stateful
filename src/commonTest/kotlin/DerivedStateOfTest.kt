import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.derivedStateOf
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.stateBound
import dev.deftu.stateful.dsl.trackedStateBound
import kotlin.test.Test
import kotlin.test.assertEquals

class DerivedStateOfTest {
    @Test
    fun aDerivedStateRecomputesOnEveryRead() {
        var computations = 0
        val source = mutableStateOf(1)
        val doubled = derivedStateOf {
            computations++
            source.value * 2
        }

        assertEquals(2, doubled.value)
        assertEquals(2, doubled.value)

        assertEquals(2, computations)
    }

    @Test
    fun aTrackedReadOfADerivedStateRegistersItsSources() {
        val source = mutableStateOf(1)
        val doubled = derivedStateOf { source() * 2 }
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(doubled()) }
            owner
        }

        source.set(2)

        assertEquals(listOf(2, 4), observed)
        owner.dispose()
    }

    @Test
    fun aDerivedStateHasNoEqualityCutoffWhereAMemoDoes() {
        val source = mutableStateOf(1)
        val derivedParity = derivedStateOf { source() % 2 }
        val memoParity = memo { source() % 2 }

        val fromDerived = mutableListOf<Int>()
        val fromMemo = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { fromDerived.add(derivedParity()) }
            effect { fromMemo.add(memoParity()) }
            owner
        }

        source.set(3)

        assertEquals(listOf(1, 1), fromDerived)
        assertEquals(listOf(1), fromMemo)

        owner.dispose()
    }

    @Test
    fun theUntrackedReadOfADerivedStateRegistersNothing() {
        val source = mutableStateOf(1)
        val doubled = derivedStateOf { source() * 2 }
        var runs = 0

        val outer = memo {
            runs++
            doubled.value
        }

        assertEquals(2, outer.value)

        source.set(5)

        assertEquals(2, outer.value)
        assertEquals(1, runs)
    }

    @Test
    fun aDerivedStateCanBeSubscribedTo() {
        val source = mutableStateOf(1)
        val doubled = derivedStateOf { source() * 2 }
        val observed = mutableListOf<Int>()

        val subscription = doubled.subscribe { value -> observed.add(value) }

        source.set(2)
        source.set(3)

        assertEquals(listOf(4, 6), observed)
        subscription.dispose()
    }

    @Test
    fun anUntrackedDelegateDoesNotWakeItsReader() {
        val source = mutableStateOf(1)
        val holder = object {
            val value: Int by stateBound(source)
        }
        var runs = 0

        val derived = memo {
            runs++
            holder.value
        }

        assertEquals(1, derived.value)

        source.set(2)

        assertEquals(1, derived.value)
        assertEquals(1, runs)
    }

    @Test
    fun aTrackedDelegateWakesItsReader() {
        val source = mutableStateOf(1)
        val holder = object {
            val value: Int by trackedStateBound(source)
        }
        var runs = 0

        val derived = memo {
            runs++
            holder.value
        }

        assertEquals(1, derived.value)

        source.set(2)

        assertEquals(2, derived.value)
        assertEquals(2, runs)
    }
}
