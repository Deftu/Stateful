import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.dsl.zippedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ZippedStateTest {
    @Test
    fun zippedStateReturnsInitialPair() {
        val zipped = zippedStateOf(stateOf(1), stateOf(2))
        assertEquals(1 to 2, zipped.get())
    }

    @Test
    fun zippedStateFollowsBothSources() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val zipped = zippedStateOf(first, second)

        first.set(10)
        assertEquals(10 to 2, zipped.get())

        second.set(20)
        assertEquals(10 to 20, zipped.get())
    }

    @Test
    fun rebindFirstUpdatesFirstValue() {
        val zipped = zippedStateOf(stateOf(1), stateOf(2))

        zipped.rebindFirst(stateOf(3))

        assertEquals(3 to 2, zipped.get())
    }

    @Test
    fun rebindSecondUpdatesSecondValue() {
        val zipped = zippedStateOf(stateOf(1), stateOf(2))

        zipped.rebindSecond(stateOf(3))

        assertEquals(1 to 3, zipped.get())
    }

    @Test
    fun rebindFirstSurvivesLaterSecondUpdates() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val zipped = zippedStateOf(first, second)

        zipped.rebindFirst(stateOf(3))
        second.set(20)

        assertEquals(3 to 20, zipped.get())
    }

    @Test
    fun rebindSecondSurvivesLaterFirstUpdates() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val zipped = zippedStateOf(first, second)

        zipped.rebindSecond(stateOf(3))
        first.set(10)

        assertEquals(10 to 3, zipped.get())
    }

    @Test
    fun rebindDetachesTheOldSource() {
        val first = mutableStateOf(1)
        val zipped = zippedStateOf(first, stateOf(2))

        zipped.rebindFirst(stateOf(3))
        first.set(99)

        assertEquals(3 to 2, zipped.get())
    }

    @Test
    fun zippedStateNotifiesSubscribers() {
        val first = mutableStateOf(1)
        val zipped = zippedStateOf(first, stateOf(2))

        val observed = mutableListOf<Pair<Int, Int>>()
        zipped.subscribe { value -> observed.add(value) }

        first.set(10)

        assertEquals(listOf(10 to 2), observed)
    }
}
