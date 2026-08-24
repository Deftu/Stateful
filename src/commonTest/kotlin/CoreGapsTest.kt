import dev.deftu.stateful.StateWarnings
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.runWithOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreGapsTest {
    @Test
    fun severalWritesBeforeADrainScheduleTheEffectOnce() {
        val scheduler = Scheduler.queued()
        val source = mutableStateOf(0)
        var runs = 0

        val root = createOwner(scheduler)
        runWithOwner(root) { effect { source(); runs++ } }
        scheduler.drain()

        assertEquals(1, runs)

        repeat(10) { index -> source.set(index + 1) }

        assertEquals(1, scheduler.size)

        scheduler.drain()

        assertEquals(2, runs)
        root.dispose()
    }

    @Test
    fun anEffectWritingFromItsOwnBodySchedulesTheNextRun() {
        val scheduler = Scheduler.queued()
        val trigger = mutableStateOf(0)
        val mirror = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner(scheduler)
        runWithOwner(root) {
            effect { mirror.set(trigger()) }
            effect { observed.add(mirror()) }
        }
        scheduler.drain()
        observed.clear()

        trigger.set(5)
        scheduler.drain()

        assertEquals(listOf(5), observed)
        root.dispose()
    }

    @Test
    fun anOwnerReportsWhatItHolds() {
        val source = mutableStateOf(0)
        val owner = createOwner()

        assertTrue(owner.isEmpty)

        runWithOwner(owner) {
            effect { source() }
            effect { source() }
            onCleanup { }
        }

        assertEquals(2, owner.computationCount)
        assertEquals(2, owner.childCount)
        assertEquals(1, owner.cleanupCount)
        assertFalse(owner.isEmpty)

        owner.dispose()

        assertTrue(owner.isEmpty)
    }

    @Test
    fun aDisposedOwnerHoldsNothing() {
        val source = mutableStateOf(0)
        val owner = createRoot { owner ->
            effect { source() }
            onCleanup { }
            owner
        }

        owner.dispose()

        assertEquals(0, owner.childCount)
        assertEquals(0, owner.cleanupCount)
        assertEquals(0, owner.computationCount)
    }

    @Test
    fun anOrphanedEffectReportsThroughTheWarningHook() {
        val warnings = mutableListOf<String>()
        val previous = StateWarnings.handler
        StateWarnings.handler = { message -> warnings.add(message) }

        try {
            val source = mutableStateOf(0)
            val handle = effect { source() }
            handle.dispose()
        } finally {
            StateWarnings.handler = previous
        }

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("createRoot"), warnings.single())
    }

    @Test
    fun aCleanupOutsideAnyOwnerWarnsRatherThanThrowing() {
        val warnings = mutableListOf<String>()
        val previous = StateWarnings.handler
        StateWarnings.handler = { message -> warnings.add(message) }

        try {
            onCleanup { }
        } finally {
            StateWarnings.handler = previous
        }

        assertEquals(1, warnings.size)
    }

    @Test
    fun trackingReachesThroughAForeignStackFrame() {
        val source = mutableStateOf(1)
        var runs = 0

        val derived = memo {
            runs++
            renderThroughACallback { source() * 2 }
        }

        assertEquals(2, derived.value)
        assertEquals(1, runs)

        source.set(3)

        assertEquals(6, derived.value)
        assertEquals(2, runs)
    }

    @Test
    fun trackingReachesThroughANestedForeignCallback() {
        val first = mutableStateOf("a")
        val second = mutableStateOf("b")

        val joined = memo {
            renderThroughACallback {
                renderThroughACallback { first() } + renderThroughACallback { second() }
            }
        }

        assertEquals("ab", joined.value)

        second.set("B")

        assertEquals("aB", joined.value)
    }

    @Test
    fun aStateReadInsideAnUnrelatedLibrarysVisitorRegisters() {
        val name = mutableStateOf("world")
        val document = FakeDocument(listOf(FakeNode { "hello, ${name()}" }))
        val rendered = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { rendered.add(document.render()) }
            owner
        }

        name.set("there")

        assertEquals(listOf("hello, world", "hello, there"), rendered)
        owner.dispose()
    }

    private fun <T> renderThroughACallback(block: () -> T): T = block()

    private class FakeNode(val emit: () -> String)

    private class FakeDocument(private val nodes: List<FakeNode>) {
        fun render(): String = nodes.joinToString("") { node -> node.emit() }
    }
}
