import dev.deftu.stateful.utils.stateOf
import dev.deftu.stateful.utils.zippedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ZippedStateTest {

    @Test
    fun `rebindFirst should update the first value`() {
        val first = stateOf(1)
        val second = stateOf(2)
        val zipped = zippedStateOf(first, second)

        zipped.rebindFirst(stateOf(3))

        assertEquals(3 to 2, zipped.get())
    }

    @Test
    fun `rebindSecond should update the second value`() {
        val first = stateOf(1)
        val second = stateOf(2)
        val zipped = zippedStateOf(first, second)

        zipped.rebindSecond(stateOf(3))

        assertEquals(1 to 3, zipped.get())
    }

}
