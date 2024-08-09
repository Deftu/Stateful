import dev.deftu.stateful.utils.*
import kotlin.test.Test
import kotlin.test.assertEquals

class StringDslTest {

    @Test
    fun `State string contains should return true`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world")
        val result = state.contains(other)
        assertEquals(true, result.get())
    }

    @Test
    fun `State string contains should return false`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Heya!")
        val result = state.contains(other)
        assertEquals(false, result.get())
    }

    @Test
    fun `State to string contains should return true`() {
        val state = stateOf("Hello, world!")
        val other = "world"
        val result = state.contains(other)
        assertEquals(true, result.get())
    }

    @Test
    fun `State to string contains should return false`() {
        val state = stateOf("Hello, world!")
        val other = "Heya!"
        val result = state.contains(other)
        assertEquals(false, result.get())
    }

    @Test
    fun `State string startsWith should return true`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello")
        val result = state.startsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun `State string startsWith should return false`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world!")
        val result = state.startsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun `State to string startsWith should return true`() {
        val state = stateOf("Hello, world!")
        val other = "Hello"
        val result = state.startsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun `State to string startsWith should return false`() {
        val state = stateOf("Hello, world!")
        val other = "world!"
        val result = state.startsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun `State string endsWith should return true`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("world!")
        val result = state.endsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun `State string endsWith should return false`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello")
        val result = state.endsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun `State to string endsWith should return true`() {
        val state = stateOf("Hello, world!")
        val other = "world!"
        val result = state.endsWith(other)
        assertEquals(true, result.get())
    }

    @Test
    fun `State to string endsWith should return false`() {
        val state = stateOf("Hello, world!")
        val other = "Hello"
        val result = state.endsWith(other)
        assertEquals(false, result.get())
    }

    @Test
    fun `State string equals should return true`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello, world!")
        val result = state == other
        assertEquals(true, result)
    }

    @Test
    fun `State string equals should return false`() {
        val state = stateOf("Hello, world!")
        val other = stateOf("Hello, world")
        val result = state == other
        assertEquals(false, result)
    }

    @Test
    fun `State string isEmpty should return false`() {
        val state = stateOf("Hello, world!")
        val result = state.isEmpty()
        assertEquals(false, result.get())
    }

    @Test
    fun `State string isNotEmpty should return true`() {
        val state = stateOf("Hello, world!")
        val result = state.isNotEmpty()
        assertEquals(true, result.get())
    }

    @Test
    fun `State string isEmpty should return true`() {
        val state = stateOf("")
        val result = state.isEmpty()
        assertEquals(true, result.get())
    }

    @Test
    fun `State string isNotEmpty should return false`() {
        val state = stateOf("")
        val result = state.isNotEmpty()
        assertEquals(false, result.get())
    }

}
