import dev.deftu.stateful.Equality
import dev.deftu.stateful.State
import dev.deftu.stateful.combine
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.mappedStateOf
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.runWithOwner
import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.flatMap
import dev.deftu.stateful.getOrDefault
import dev.deftu.stateful.map
import dev.deftu.stateful.zip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every replacement the migration guide offers, run against the real API.
 *
 * A migration guide is read exactly once per consumer, by someone whose code does not compile. An
 * example that is wrong there costs more than one that is wrong anywhere else.
 */
class MigrationSamplesTest {
    private class View {
        var title: String = ""
        var detached: Boolean = false
    }

    @Test
    fun theDiamondNoLongerProducesAValueThatNeverExisted() {
        val root = mutableStateOf(1)
        val a = root.map { it * 2 }
        val b = root.map { it * 3 }
        val sum = a.combine(b) { x, y -> x + y }
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(sum()) }
            owner
        }

        root.set(2)

        assertEquals(listOf(5, 10), observed)
        assertFalse(observed.contains(7))
        owner.dispose()
    }

    @Test
    fun bothReplacementReadsBehaveAsDocumented() {
        val state = mutableStateOf("value")
        var trackedRuns = 0
        var untrackedRuns = 0

        val tracked = memo { trackedRuns++; state() }
        val untracked = memo { untrackedRuns++; state.value }

        assertEquals("value", tracked.value)
        assertEquals("value", untracked.value)

        state.set("changed")

        assertEquals("changed", tracked.value)
        assertEquals(2, trackedRuns)

        assertEquals("value", untracked.value, "an untracked read does not re-run its memo")
        assertEquals(1, untrackedRuns)
    }

    @Test
    fun theUnwrappingReadsNowTrack() {
        val source = mutableStateOf<String?>(null)
        var runs = 0
        val derived = memo { runs++; source.getOrDefault("fallback") }

        assertEquals("fallback", derived.value)

        source.set("present")

        assertEquals("present", derived.value)
        assertEquals(2, runs)
    }

    @Test
    fun theUntrackedUnwrappingFormStillExists() {
        val source = mutableStateOf<String?>(null)
        assertEquals("fallback", source.value ?: "fallback")
    }

    @Test
    fun theLifetimeSampleTearsDownInOrder() {
        val view = View()
        val title = mutableStateOf("first")

        val owner = createRoot { owner ->
            effect { view.title = title() }
            onCleanup { view.detached = true }
            owner
        }

        title.set("second")
        assertEquals("second", view.title)

        owner.dispose()

        assertTrue(view.detached)
        title.set("third")
        assertEquals("second", view.title)
    }

    @Test
    fun theHostLifetimeSampleWorksAcrossCalls() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        val root = createOwner()

        runWithOwner(root) { effect { observed.add(source()) } }
        source.set(1)
        root.dispose()
        source.set(2)

        assertEquals(listOf(0, 1), observed)
    }

    @Test
    fun everyRemovedTypeHasAWorkingReplacement() {
        val source = mutableStateOf(2)
        val other = mutableStateOf(5)

        assertEquals(4, memo { source() * 2 }.value)
        assertEquals(4, source.map { it * 2 }.value)
        assertEquals(2 to 5, memo { source() to other() }.value)
        assertEquals(2 to 5, source.zip(other).value)

        val holder = mutableStateOf<State<Int>>(source)
        assertEquals(2, memo { holder()() }.value)
        assertEquals(2, holder.flatMap { it }.value)

        assertEquals(4, mappedStateOf(source) { it * 2 }.value)
    }

    @Test
    fun theRebindReplacementFollowsANewSource() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(10)

        val source = mutableStateOf<State<Int>>(first)
        val mapped = source.flatMap { it }.map { it * 2 }

        assertEquals(2, mapped.value)

        source.set(second)
        assertEquals(20, mapped.value)

        first.set(99)
        assertEquals(20, mapped.value)
    }

    @Test
    fun neverEqualReplacesNotifyCurrent() {
        val source = mutableStateOf(1, Equality.never())
        val observed = mutableListOf<Int>()
        val subscription = source.subscribe { observed.add(it) }

        source.set(1)
        source.set(1)

        assertEquals(listOf(1, 1), observed)
        subscription.dispose()
    }

    @Test
    fun statesCompareByIdentityAndValuesCompareByValue() {
        assertFalse(stateOf("a") == stateOf("a"))
        assertEquals(stateOf("a").value, stateOf("a").value)
    }
}
