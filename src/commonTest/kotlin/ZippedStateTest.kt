import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.dsl.zippedStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import dev.deftu.stateful.flatMap
import dev.deftu.stateful.State

class ZippedStateTest {
    @Test
    fun zippedStateReturnsInitialPair() {
        val zipped = zippedStateOf(stateOf(1), stateOf(2))
        assertEquals(1 to 2, zipped.value)
    }

    @Test
    fun zippedStateFollowsBothSources() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val zipped = zippedStateOf(first, second)

        first.set(10)
        assertEquals(10 to 2, zipped.value)

        second.set(20)
        assertEquals(10 to 20, zipped.value)
    }

    @Test
    fun zippedStateFollowsASwappedSource() {
        val first = mutableStateOf(1)
        val replacement = mutableStateOf(3)
        val source = mutableStateOf<State<Int>>(first)
        val zipped = zippedStateOf(source.flatMap { it }, stateOf(2))
        assertEquals(1 to 2, zipped.value)

        source.set(replacement)
        assertEquals(3 to 2, zipped.value)

        first.set(99)
        assertEquals(3 to 2, zipped.value)
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
