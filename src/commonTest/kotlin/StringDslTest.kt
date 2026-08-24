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
        assertEquals(true, result.get())
    }

    @Test
    fun stateStringContainsReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Heya!")
        val result = state.contains(other)
        assertEquals(false, result.get())
    }

    @Test
    fun stateToStringContainsReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = "world"
        val result = state.contains(other)
        assertEquals(true, result.get())
    }

    @Test
    fun stateToStringContainsReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = "Heya!"
        val result = state.contains(other)
        assertEquals(false, result.get())
    }

    @Test
    fun stateStringStartsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello")
        val result = state.startsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun stateStringStartsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world!")
        val result = state.startsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun stateToStringStartsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = "Hello"
        val result = state.startsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun stateToStringStartsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = "world!"
        val result = state.startsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun stateStringEndsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world!")
        val result = state.endsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun stateStringEndsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello")
        val result = state.endsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun stateToStringEndsWithReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = "world!"
        val result = state.endsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun stateToStringEndsWithReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = "Hello"
        val result = state.endsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun stateStringEqualsReturnsTrue() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello, world!")
        val result = state == other
        assertEquals(true, result)
    }

    @Test
    fun stateStringEqualsReturnsFalse() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello, world")
        val result = state == other
        assertEquals(false, result)
    }

    @Test
    fun stateStringIsEmptyReturnsFalse() {
        val state = stateOf("Hello, world!")
        val result = state.isEmpty()
        assertEquals(false, result.get())
    }

    @Test
    fun stateStringIsNotEmptyReturnsTrue() {
        val state = stateOf("Hello, world!")
        val result = state.isNotEmpty()
        assertEquals(true, result.get())
    }

    @Test
    fun stateStringIsEmptyReturnsTrue() {
        val state = stateOf("")
        val result = state.isEmpty()
        assertEquals(true, result.get())
    }

    @Test
    fun stateStringIsNotEmptyReturnsFalse() {
        val state = stateOf("")
        val result = state.isNotEmpty()
        assertEquals(false, result.get())
    }
}
