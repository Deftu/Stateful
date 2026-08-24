import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.getOrDefault
import dev.deftu.stateful.getOrElse
import dev.deftu.stateful.getOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NullableOperatorsTest {
    @Test
    fun getOrDefaultReturnsTheValueWhenPresent() {
        val state = stateOf<String?>("present")
        assertEquals("present", state.getOrDefault("fallback"))
    }

    @Test
    fun getOrDefaultReturnsTheDefaultWhenNull() {
        val state = stateOf<String?>(null)
        assertEquals("fallback", state.getOrDefault("fallback"))
    }

    @Test
    fun getOrElseOnlyEvaluatesTheDefaultWhenNull() {
        var evaluations = 0
        val present = stateOf<String?>("present")

        assertEquals("present", present.getOrElse { evaluations++; "fallback" })
        assertEquals(0, evaluations)

        val absent = stateOf<String?>(null)

        assertEquals("fallback", absent.getOrElse { evaluations++; "fallback" })
        assertEquals(1, evaluations)
    }

    @Test
    fun getOrThrowReturnsTheValueWhenPresent() {
        val state = stateOf<String?>("present")
        assertEquals("present", state.getOrThrow())
    }

    @Test
    fun getOrThrowThrowsTheGivenExceptionWhenNull() {
        val state = stateOf<String?>(null)
        assertFailsWith<UnsupportedOperationException> {
            state.getOrThrow(UnsupportedOperationException("absent"))
        }
    }

    @Test
    fun getOrThrowThrowsTheSuppliedExceptionWhenNull() {
        val state = stateOf<String?>(null)
        assertFailsWith<UnsupportedOperationException> {
            state.getOrThrow { UnsupportedOperationException("absent") }
        }
    }

    @Test
    fun getOrThrowCarriesTheGivenMessage() {
        val state = stateOf<String?>(null)
        val failure = assertFailsWith<IllegalStateException> { state.getOrThrow("was absent") }

        assertEquals("was absent", failure.message)
    }

    @Test
    fun theseOperatorsResolveOnANonNullableState() {
        val state = mutableStateOf("present")
        assertEquals("present", state.getOrDefault("fallback"))
        assertEquals("present", state.getOrThrow())
    }

    @Test
    fun theseOperatorsReadTracked() {
        val source = mutableStateOf<String?>(null)
        var runs = 0

        val derived = dev.deftu.stateful.dsl.memo {
            runs++
            source.getOrDefault("fallback")
        }

        assertEquals("fallback", derived.value)

        source.set("present")

        assertEquals("present", derived.value)
        assertEquals(2, runs)
    }
}
