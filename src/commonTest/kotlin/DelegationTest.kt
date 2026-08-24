import dev.deftu.stateful.dsl.mutableStateBound
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.stateBound
import dev.deftu.stateful.dsl.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class DelegationTest {
    @Test
    fun stateDelegateReturnsInitialValue() {
        val state = stateOf(10)
        assertEquals(10, state.get())

        val value by stateBound(state)
        assertEquals(10, value)
    }

    @Test
    fun mutableStateDelegateUpdatesCorrectly() {
        val state = mutableStateOf(10)
        assertEquals(10, state.get())

        var value by mutableStateBound(state)
        assertEquals(10, value)

        value = 20
        assertEquals(20, value)
    }
}
