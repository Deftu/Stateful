import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.ext.and
import dev.deftu.stateful.ext.not
import dev.deftu.stateful.ext.or
import kotlin.test.Test
import kotlin.test.assertEquals

class BooleanDslTest {
    @Test
    fun stateBooleanAndReturnsTrue() {
        val state = stateOf(true)
        val other = stateOf(true)
        val result = state and other
        assertEquals(true, result.value)
    }

    @Test
    fun stateBooleanAndReturnsFalse() {
        val state = stateOf(true)
        val other = stateOf(false)
        val result = state and other
        assertEquals(false, result.value)
    }

    @Test
    fun stateAndBooleanReturnsFalse() {
        val state = stateOf(true)
        val other = false
        val result = state and other
        assertEquals(false, result.value)
    }

    @Test
    fun stateAndStateFalseReturnsFalse() {
        val state = stateOf(false)
        val other = stateOf(false)
        val result = state and other
        assertEquals(false, result.value)
    }

    @Test
    fun booleanAndStateReturnsTrue() {
        val state = true
        val other = stateOf(true)
        val result = state and other
        assertEquals(true, result.value)
    }

    @Test
    fun booleanAndStateReturnsFalse() {
        val state = false
        val other = stateOf(true)
        val result = state and other
        assertEquals(false, result.value)
    }

    @Test
    fun stateBooleanOrReturnsTrue() {
        val state = stateOf(true)
        val other = stateOf(true)
        val result = state or other
        assertEquals(true, result.value)
    }

    @Test
    fun stateBooleanOrReturnsTrueIfOneTrue() {
        val state = stateOf(true)
        val other = stateOf(false)
        val result = state or other
        assertEquals(true, result.value)
    }

    @Test
    fun stateOrBooleanReturnsTrue() {
        val state = stateOf(true)
        val other = false
        val result = state or other
        assertEquals(true, result.value)
    }

    @Test
    fun stateOrStateFalseReturnsFalse() {
        val state = stateOf(false)
        val other = stateOf(false)
        val result = state or other
        assertEquals(false, result.value)
    }

    @Test
    fun booleanOrStateReturnsTrue() {
        val state = true
        val other = stateOf(true)
        val result = state or other
        assertEquals(true, result.value)
    }

    @Test
    fun booleanOrStateReturnsTrueIfStateTrue() {
        val state = false
        val other = stateOf(true)
        val result = state or other
        assertEquals(true, result.value)
    }

    @Test
    fun stateNotReturnsFalse() {
        val state = stateOf(true)
        val result = !state
        assertEquals(false, result.value)
    }
}
