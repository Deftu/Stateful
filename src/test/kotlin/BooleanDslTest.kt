import dev.deftu.stateful.utils.and
import dev.deftu.stateful.utils.not
import dev.deftu.stateful.utils.or
import dev.deftu.stateful.utils.stateOf
import kotlin.test.Test
import kotlin.test.assertEquals

class BooleanDslTest {
    
    @Test
    fun `State boolean and should return true`() {
        val state = stateOf(true)
        val other = stateOf(true)
        val result = state and other
        assertEquals(true, result.get())
    }
    
    @Test
    fun `State boolean and should return false`() {
        val state = stateOf(true)
        val other = stateOf(false)
        val result = state and other
        assertEquals(false, result.get())
    }
    
    @Test
    fun `State to boolean and should return true`() {
        val state = stateOf(true)
        val other = false
        val result = state and other
        assertEquals(false, result.get())
    }
    
    @Test
    fun `State to boolean and should return false`() {
        val state = stateOf(false)
        val other = stateOf(false)
        val result = state and other
        assertEquals(false, result.get())
    }
    
    @Test
    fun `Boolean to state and should return true`() {
        val state = true
        val other = stateOf(true)
        val result = state and other
        assertEquals(true, result.get())
    }
    
    @Test
    fun `Boolean to state and should return false`() {
        val state = false
        val other = stateOf(true)
        val result = state and other
        assertEquals(false, result.get())
    }

    @Test
    fun `State boolean or should return true`() {
        val state = stateOf(true)
        val other = stateOf(true)
        val result = state or other
        assertEquals(true, result.get())
    }

    @Test
    fun `State boolean or should return false`() {
        val state = stateOf(true)
        val other = stateOf(false)
        val result = state or other
        assertEquals(true, result.get())
    }

    @Test
    fun `State to boolean or should return true`() {
        val state = stateOf(true)
        val other = false
        val result = state or other
        assertEquals(true, result.get())
    }

    @Test
    fun `State to boolean or should return false`() {
        val state = stateOf(false)
        val other = stateOf(false)
        val result = state or other
        assertEquals(false, result.get())
    }

    @Test
    fun `Boolean to state or should return true`() {
        val state = true
        val other = stateOf(true)
        val result = state or other
        assertEquals(true, result.get())
    }

    @Test
    fun `Boolean to state or should return false`() {
        val state = false
        val other = stateOf(true)
        val result = state or other
        assertEquals(true, result.get())
    }

    @Test
    fun `State not should return false`() {
        val state = stateOf(true)
        val result = !state
        assertEquals(false, result.get())
    }
    
}
