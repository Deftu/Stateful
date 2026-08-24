import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.ext.contains
import dev.deftu.stateful.ext.endsWith
import dev.deftu.stateful.ext.isEmpty
import dev.deftu.stateful.ext.isNotEmpty
import dev.deftu.stateful.ext.startsWith
import kotlin.test.Test
import kotlin.test.assertEquals

class StringDslTest {
    @Test
    fun stateStringContainsReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world")
        val result = state.contains(other)
        assertEquals(true, result.value)
    }

    @Test
    fun stateStringContainsReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Heya!")
        val result = state.contains(other)
        assertEquals(false, result.value)
    }

    @Test
    fun stateToStringContainsReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = "world"
        val result = state.contains(other)
        assertEquals(true, result.value)
    }

    @Test
    fun stateToStringContainsReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = "Heya!"
        val result = state.contains(other)
        assertEquals(false, result.value)
    }

    @Test
    fun stateStringStartsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello")
        val result = state.startsWith(other)
        assertEquals(true, result.value)
    }

    @Test
    fun stateStringStartsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world!")
        val result = state.startsWith(other)
        assertEquals(false, result.value)
    }

    @Test
    fun stateToStringStartsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = "Hello"
        val result = state.startsWith(other)
        assertEquals(true, result.value)
    }

    @Test
    fun stateToStringStartsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = "world!"
        val result = state.startsWith(other)
        assertEquals(false, result.value)
    }

    @Test
    fun stateStringEndsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world!")
        val result = state.endsWith(other)
        assertEquals(true, result.value)
    }

    @Test
    fun stateStringEndsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello")
        val result = state.endsWith(other)
        assertEquals(false, result.value)
    }

    @Test
    fun stateToStringEndsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = "world!"
        val result = state.endsWith(other)
        assertEquals(true, result.value)
    }

    @Test
    fun stateToStringEndsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = "Hello"
        val result = state.endsWith(other)
        assertEquals(false, result.value)
    }

    @Test
    fun statesCompareByIdentityNotByValue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello, world!")

        assertEquals(false, state == other)
        assertEquals(true, state == state)
    }

    @Test
    fun statesHoldingEqualValuesCompareTheirValues() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello, world!")

        assertEquals(state.value, other.value)
    }

    @Test
    fun stateStringIsEmptyReturnsFalse() {
        val state = stateOf("Hello, world!")
        val result = state.isEmpty()
        assertEquals(false, result.value)
    }

    @Test
    fun stateStringIsNotEmptyReturnsTrue() {
        val state = stateOf("Hello, world!")
        val result = state.isNotEmpty()
        assertEquals(true, result.value)
    }

    @Test
    fun stateStringIsEmptyReturnsTrue() {
        val state = stateOf("")
        val result = state.isEmpty()
        assertEquals(true, result.value)
    }

    @Test
    fun stateStringIsNotEmptyReturnsFalse() {
        val state = stateOf("")
        val result = state.isNotEmpty()
        assertEquals(false, result.value)
    }
}
