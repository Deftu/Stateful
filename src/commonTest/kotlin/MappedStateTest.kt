import dev.deftu.stateful.utils.mappedStateOf
import dev.deftu.stateful.utils.mappedMutableStateOf
import dev.deftu.stateful.utils.mutableStateOf
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
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())

        state.set(2)
        assertEquals(4, mappedState.get())

        state.set(3)
        assertEquals(6, mappedState.get())

        val mappedState2 = mappedStateOf(state) { it * 3 }
        assertEquals(9, mappedState2.get())

        state.set(4)
        assertEquals(12, mappedState2.get())
    }

    @Test
    fun mappedStateReturnsCorrectValuesForRebindings() {
        val state = mutableStateOf(0)
        val mappedState = mappedStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())

        state.set(2)
        assertEquals(4, mappedState.get())

        state.set(3)
        assertEquals(6, mappedState.get())

        val state2 = mutableStateOf(0)
        val mappedState2 = mappedStateOf(state2) { it * 3 }
        assertEquals(0, mappedState2.get())

        state2.set(1)
        assertEquals(3, mappedState2.get())

        state2.set(2)
        assertEquals(6, mappedState2.get())

        state2.set(3)
        assertEquals(9, mappedState2.get())
    }

    // Mutable

    @Test
    fun mappedMutableStateReturnsCorrectValue() {
        val state = mutableStateOf(0)
        val mappedState = mappedMutableStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())
    }

    @Test
    fun mappedMutableStateReturnsCorrectValueAfterMultipleChanges() {
        val state = mutableStateOf(0)
        val mappedState = mappedMutableStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())

        state.set(2)
        assertEquals(4, mappedState.get())

        state.set(3)
        assertEquals(6, mappedState.get())
    }

    @Test
    fun mappedMutableStateReturnsCorrectValueAfterMultipleChangesWithDifferentMappers() {
        val state = mutableStateOf(0)
        val mappedState = mappedMutableStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())

        state.set(2)
        assertEquals(4, mappedState.get())

        state.set(3)
        assertEquals(6, mappedState.get())

        val mappedState2 = mappedMutableStateOf(state) { it * 3 }
        assertEquals(9, mappedState2.get())

        state.set(4)
        assertEquals(12, mappedState2.get())
    }

    @Test
    fun mappedMutableStateReturnsCorrectValuesForRebindings() {
        val state = mutableStateOf(0)
        val mappedState = mappedMutableStateOf(state) { it * 2 }
        assertEquals(0, mappedState.get())

        state.set(1)
        assertEquals(2, mappedState.get())

        state.set(2)
        assertEquals(4, mappedState.get())

        state.set(3)
        assertEquals(6, mappedState.get())

        val state2 = mutableStateOf(0)
        val mappedState2 = mappedMutableStateOf(state2) { it * 3 }
        assertEquals(0, mappedState2.get())

        state2.set(1)
        assertEquals(3, mappedState2.get())

        state2.set(2)
        assertEquals(6, mappedState2.get())

        state2.set(3)
        assertEquals(9, mappedState2.get())
    }
}
