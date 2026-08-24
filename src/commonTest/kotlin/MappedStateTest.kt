import dev.deftu.stateful.dsl.mappedStateOf
import dev.deftu.stateful.dsl.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.deftu.stateful.map
import dev.deftu.stateful.flatMap
import dev.deftu.stateful.State

class MappedStateTest {
    @Test
    fun mappedStateReturnsCorrectValue() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        assertEquals(0, mappedState.value)

        state.set(1)
        assertEquals(2, mappedState.value)
    }

    @Test
    fun mappedStateReturnsCorrectValueAfterMultipleChanges() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        assertEquals(0, mappedState.value)

        state.set(1)
        assertEquals(2, mappedState.value)

        state.set(2)
        assertEquals(4, mappedState.value)

        state.set(3)
        assertEquals(6, mappedState.value)
    }

    @Test
    fun mappedStateReturnsCorrectValueAfterMultipleChangesWithDifferentMappers() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        val mappedState2 = mappedStateOf(state) { it * 3 }

        state.set(3)
        assertEquals(6, mappedState.value)
        assertEquals(9, mappedState2.value)

        state.set(4)
        assertEquals(8, mappedState.value)
        assertEquals(12, mappedState2.value)
    }

    @Test
    fun mappedStateNotifiesSubscribers() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }

        val observed = mutableListOf<Int>()
        mappedState.subscribe { value -> observed.add(value) }

        state.set(1)
        state.set(2)

        assertEquals(listOf(2, 4), observed)
    }

    @Test
    fun chainedMappedStatesPropagate() {
        val state = mutableStateOf(0)
        val doubled = mappedStateOf(state) { it * 2 }
        val stringified = mappedStateOf(doubled) { "value: $it" }

        val observed = mutableListOf<String>()
        stringified.subscribe { value -> observed.add(value) }

        state.set(5)

        assertEquals("value: 10", stringified.value)
        assertEquals(listOf("value: 10"), observed)
    }

    @Test
    fun mappedStateFollowsASwappedSource() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(10)
        val source = mutableStateOf<State<Int>>(first)
        val mappedState = source.flatMap { it }.map { it * 2 }
        assertEquals(2, mappedState.value)

        source.set(second)
        assertEquals(20, mappedState.value)

        second.set(20)
        assertEquals(40, mappedState.value)
    }

    @Test
    fun swappingTheSourceDetachesTheOldOne() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(10)
        val source = mutableStateOf<State<Int>>(first)
        val mappedState = source.flatMap { it }.map { it * 2 }
        assertEquals(2, mappedState.value)

        source.set(second)
        first.set(99)

        assertEquals(20, mappedState.value)
    }
}
