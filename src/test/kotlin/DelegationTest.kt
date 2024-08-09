import dev.deftu.stateful.utils.mutableStateBound
import dev.deftu.stateful.utils.mutableStateOf
import dev.deftu.stateful.utils.stateBound
import dev.deftu.stateful.utils.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class DelegationTest {

    @Test
    fun `StateDelegate should return the initial value`() {
        val state = stateOf(10)
        assertEquals(10, state.get())

        val value by stateBound(state)
        assertEquals(10, value)
    }

    @Test
    fun `MutableStateDelegate should return the new value after setting it`() {
        val state = mutableStateOf(10)
        assertEquals(10, state.get())

        var value by mutableStateBound(state)
        assertEquals(10, value)

        value = 20
        assertEquals(20, value)
    }

}
