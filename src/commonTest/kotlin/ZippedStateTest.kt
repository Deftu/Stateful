import dev.deftu.stateful.ZippedState
import dev.deftu.stateful.utils.stateOf
import dev.deftu.stateful.utils.zippedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ZippedStateTest {

    @Test
    fun rebindFirstUpdatesFirstValue() {
        val first = stateOf(1)
        val second = stateOf(2)
        val zipped = zippedStateOf(first, second)

        zipped.rebindFirst(stateOf(3))

        assertEquals(ZippedState.Zip(3, 2), zipped.get())
    }

    @Test
    fun rebindSecondUpdatesSecondValue() {
        val first = stateOf(1)
        val second = stateOf(2)
        val zipped = zippedStateOf(first, second)

        zipped.rebindSecond(stateOf(3))

        assertEquals(ZippedState.Zip(1, 3), zipped.get())
    }

}
