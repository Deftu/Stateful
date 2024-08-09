import dev.deftu.stateful.utils.mutableStateOf
import dev.deftu.stateful.utils.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleStateTest {

    @Test
    fun `SimpleState should return the initial value`() {
        val state = stateOf(10)
        assertEquals(10, state.get())
    }

    @Test
    fun `SimpleMutableState should return the new value after setting it`() {
        val state = mutableStateOf(10)
        assertEquals(10, state.get())

        state.set(20)
        assertEquals(20, state.get())
    }

}
