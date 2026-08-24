import dev.deftu.stateful.dsl.mappedStateOf
import dev.deftu.stateful.dsl.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class MappedStateTest {
    @Test
    fun mappedStateReturnsCorrectValue() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())
    }

    @Test
    fun mappedStateReturnsCorrectValueAfterMultipleChanges() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())

        state.set(2)
        assertEquals(4, mappedState.get())

        state.set(3)
        assertEquals(6, mappedState.get())
    }

    @Test
    fun mappedStateReturnsCorrectValueAfterMultipleChangesWithDifferentMappers() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        val mappedState2 = mappedStateOf(state) { it * 3 }

        state.set(3)
        assertEquals(6, mappedState.get())
        assertEquals(9, mappedState2.get())

        state.set(4)
        assertEquals(8, mappedState.get())
        assertEquals(12, mappedState2.get())
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

        assertEquals("value: 10", stringified.get())
        assertEquals(listOf("value: 10"), observed)
    }

    @Test
    fun mappedStateRebindsToTheNewSource() {
        val state = mutableStateOf(1)
        val other = mutableStateOf(10)
        val mappedState = mappedStateOf(state) { it * 2 }
        assertEquals(2, mappedState.get())

        mappedState.rebind(other)
        assertEquals(20, mappedState.get())

        other.set(20)
        assertEquals(40, mappedState.get())
    }

    @Test
    fun mappedStateRebindDetachesTheOldSource() {
        val state = mutableStateOf(1)
        val mappedState = mappedStateOf(state) { it * 2 }

        mappedState.rebind(mutableStateOf(10))
        state.set(99)

        assertEquals(20, mappedState.get())
    }

    @Test
    fun disposedMappedStateStopsFollowingItsSource() {
        val state = mutableStateOf(1)
        val mappedState = mappedStateOf(state) { it * 2 }

        mappedState.dispose()
        state.set(50)

        assertEquals(2, mappedState.get())
    }
}
