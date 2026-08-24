import dev.deftu.stateful.dsl.combineStateOf
import dev.deftu.stateful.dsl.flatMappedStateOf
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.dsl.zippedStateOf
import dev.deftu.stateful.ext.first
import dev.deftu.stateful.ext.second
import dev.deftu.stateful.ext.third
import kotlin.test.Test
import kotlin.test.assertEquals

class DerivedStateTest {
    @Test
    fun combineStateOfFollowsBothSources() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val combined = combineStateOf(first, second) { a, b -> a + b }
        assertEquals(3, combined.value)

        first.set(10)
        assertEquals(12, combined.value)

        second.set(20)
        assertEquals(30, combined.value)
    }

    @Test
    fun combineStateOfNotifiesSubscribers() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val combined = combineStateOf(first, second) { a, b -> a + b }

        val observed = mutableListOf<Int>()
        combined.subscribe { value -> observed.add(value) }

        first.set(10)
        second.set(20)

        assertEquals(listOf(12, 30), observed)
    }

    @Test
    fun combineStateOfSupportsThreeSources() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val third = mutableStateOf(3)
        val combined = combineStateOf(first, second, third) { a, b, c -> a + b + c }
        assertEquals(6, combined.value)

        third.set(30)
        assertEquals(33, combined.value)
    }

    @Test
    fun zippedStateOfSupportsThreeSources() {
        val first = mutableStateOf(1)
        val zipped = zippedStateOf(first, stateOf(2), stateOf(3))
        assertEquals(Triple(1, 2, 3), zipped.value)

        first.set(10)
        assertEquals(Triple(10, 2, 3), zipped.value)
    }

    @Test
    fun pairAccessorsTrackTheSource() {
        val source = mutableStateOf(1 to 2)
        val first = source.first()
        val second = source.second()

        source.set(10 to 20)

        assertEquals(10, first.value)
        assertEquals(20, second.value)
    }

    @Test
    fun tripleAccessorsTrackTheSource() {
        val source = mutableStateOf(Triple(1, 2, 3))
        val third = source.third()

        source.set(Triple(1, 2, 30))

        assertEquals(30, third.value)
    }

    @Test
    fun flatMappedStateFollowsTheSelectedInnerState() {
        val left = mutableStateOf("left")
        val right = mutableStateOf("right")
        val selector = mutableStateOf(true)
        val flattened = flatMappedStateOf(selector) { useLeft -> if (useLeft) left else right }
        assertEquals("left", flattened.value)

        left.set("left updated")
        assertEquals("left updated", flattened.value)

        selector.set(false)
        assertEquals("right", flattened.value)

        right.set("right updated")
        assertEquals("right updated", flattened.value)
    }

    @Test
    fun flatMappedStateDetachesTheOldInnerState() {
        val left = mutableStateOf("left")
        val right = mutableStateOf("right")
        val selector = mutableStateOf(true)
        val flattened = flatMappedStateOf(selector) { useLeft -> if (useLeft) left else right }

        selector.set(false)
        left.set("left updated")

        assertEquals("right", flattened.value)
    }

    @Test
    fun flatMappedStateNotifiesSubscribers() {
        val left = mutableStateOf("left")
        val selector = mutableStateOf(true)
        val flattened = flatMappedStateOf(selector) { left }

        val observed = mutableListOf<String>()
        flattened.subscribe { value -> observed.add(value) }

        left.set("updated")

        assertEquals(listOf("updated"), observed)
    }

}
