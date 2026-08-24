import dev.deftu.stateful.Equality
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.collections.mapKeyed
import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.rawStateOf
import dev.deftu.stateful.dsl.runWithOwner
import dev.deftu.stateful.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The library's worked examples, asserting what each one's annotations claim.
 *
 * The annotations matter as much as the code: a sample saying "prints 10, once" is a behavioural
 * claim, and an unexecuted claim is an untested one.
 */
class SpecSamplesTest {
    private data class Point(val x: Int, val y: Int)

    private data class Item(val id: Int, val name: String)

    private class RowView {
        var label: String = ""
        var position: Int = -1
    }

    private class View {
        var title: String = ""
        var subtitle: String = ""
        var detached: Boolean = false
    }

    @Test
    fun theBasicsPrintWhatTheyClaim() {
        val printed = mutableListOf<String>()
        val count = mutableStateOf(0)
        val doubled = memo { count() * 2 }

        val owner = createRoot { owner ->
            effect { printed.add("count=${count()} doubled=${doubled()}") }
            owner
        }

        assertEquals(listOf("count=0 doubled=0"), printed)

        count.set(1)
        assertEquals("count=1 doubled=2", printed.last())

        count.update { it + 1 }
        assertEquals("count=2 doubled=4", printed.last())
        assertEquals(3, printed.size)

        owner.dispose()
    }

    @Test
    fun theTrackedAndUntrackedSampleBehavesAsAnnotated() {
        val name = mutableStateOf("world")
        val greeting = memo { "hello, ${name()}" }
        val logged = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect {
                val current = name()
                val once = name.value
                logged.add("$current/$once")
            }
            owner
        }

        name.set("there")

        assertEquals(listOf("world/world", "there/there"), logged)
        assertEquals("hello, there", greeting.value)

        owner.dispose()
    }

    @Test
    fun theDiamondPrintsTenOnceAndNeverSeven() {
        val root = mutableStateOf(1)
        val a = memo { root() * 2 }
        val b = memo { root() * 3 }
        val sum = memo { a() + b() }
        val printed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { printed.add(sum()) }
            owner
        }

        root.set(2)

        assertEquals(listOf(5, 10), printed)
        assertTrue(!printed.contains(7))

        owner.dispose()
    }

    @Test
    fun theScreenSampleDisposesDepthFirstWithCleanupsReversed() {
        val view = View()
        val title = mutableStateOf("a")
        val subtitle = mutableStateOf("b")
        val order = mutableListOf<String>()

        val owner: Owner = createRoot { owner ->
            effect { view.title = title() }
            effect { view.subtitle = subtitle() }
            onCleanup { order.add("first") }
            onCleanup { view.detached = true; order.add("second") }
            owner
        }

        title.set("changed")
        assertEquals("changed", view.title)

        owner.dispose()

        assertEquals(listOf("second", "first"), order)
        assertTrue(view.detached)
    }

    @Test
    fun theSchedulerSampleRoutesEffectsThroughIt() {
        val queued = mutableListOf<() -> Unit>()
        val color = mutableStateOf("red")
        val applied = mutableListOf<String>()

        val owner = createRoot(scheduler = { task -> queued.add(task) }) { owner ->
            effect { applied.add(color()) }
            owner
        }

        assertEquals(1, queued.size)
        queued.removeFirst().invoke()
        assertEquals(listOf("red"), applied)

        owner.dispose()
    }

    @Test
    fun theBatchSamplePrintsThirtyOnce() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val printed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { printed.add(first() + second()) }
            owner
        }

        assertEquals(listOf(3), printed)

        val insideBatch = batch {
            first.set(10)
            second.set(20)
            first.value
        }

        assertEquals(10, insideBatch)
        assertEquals(listOf(3, 30), printed)

        owner.dispose()
    }

    @Test
    fun theEqualitySampleBehavesAsAnnotated() {
        val position = mutableStateOf(Point(0, 0), Equality.referential())
        val ticks = mutableStateOf(0, Equality.never())
        val count = mutableStateOf(1)
        val parity = memo(Equality.structural()) { count() % 2 }

        val moves = mutableListOf<Point>()
        val beats = mutableListOf<Int>()
        var parityRuns = 0

        val owner = createRoot { owner ->
            effect { moves.add(position()) }
            effect { beats.add(ticks()) }
            effect { parity(); parityRuns++ }
            owner
        }

        position.set(Point(0, 0))
        assertEquals(2, moves.size, "referential equality treats an equal instance as a change")

        ticks.set(0)
        assertEquals(2, beats.size, "never-equal fires on an identical write")

        parityRuns = 0
        count.set(3)
        assertEquals(0, parityRuns, "dependents wake only when the parity flips")

        count.set(2)
        assertEquals(1, parityRuns)

        owner.dispose()
    }

    @Test
    fun theCollectionSampleNotifiesAsAnnotated() {
        val items = reactiveListOf("a", "b", "c")
        val zero = mutableListOf<String>()
        val sizes = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { zero.add(items[0]) }
            effect { sizes.add(items.size) }
            owner
        }

        items[1] = "B"

        assertEquals(listOf("a"), zero, "replacing another element wakes neither")
        assertEquals(listOf(3), sizes)

        items.add("d")

        assertEquals(listOf(3, 4), sizes, "appending wakes the size consumer")
        assertEquals(listOf("a", "a"), zero, "an indexed read also registers on the structure")

        owner.dispose()
    }

    @Test
    fun theKeyedSampleMovesRowsRatherThanRebuilding() {
        val items = reactiveListOf(Item(1, "one"), Item(2, "two"))
        var builds = 0

        val owner = createRoot { owner ->
            items.mapKeyed(key = { item -> item.id }) { item, index ->
                builds++
                RowView().apply {
                    effect { label = item().name }
                    effect { position = index() }
                }
            }
            owner
        }

        assertEquals(2, builds)

        items.move(0, 1)

        assertEquals(2, builds, "reordering moves rows rather than rebuilding them")
        owner.dispose()
    }

    @Test
    fun theRawAndSnapshotSampleBehavesAsAnnotated() {
        val blob = listOf("large")
        val payload = rawStateOf(blob)
        val settings = mutableStateOf(mapOf("theme" to listOf("dark")))

        val observed = mutableListOf<List<String>>()
        val subscription = payload.subscribe { value -> observed.add(value) }

        payload.set(blob)
        assertEquals(0, observed.size, "the same instance does not propagate")

        payload.set(listOf("large"))
        assertEquals(1, observed.size, "a different instance does, even when equal")

        val plain = snapshot(settings)
        settings.set(mapOf("theme" to listOf("light")))

        assertEquals(mapOf("theme" to listOf("dark")), plain)

        subscription.dispose()
    }

    @Test
    fun theSubscribePairIsWhatAnExternalStoreNeeds() {
        val state = mutableStateOf(1)
        var notifications = 0

        val subscription = state.subscribe { notifications++ }
        val snapshotBefore = state.value

        state.set(2)

        assertEquals(1, notifications, "subscribe reports that something changed")
        assertEquals(1, snapshotBefore)
        assertEquals(2, state.value, "the snapshot is an untracked read")

        subscription.dispose()
    }

    @Test
    fun theMigrationTableMapsOntoWorkingCalls() {
        val source = mutableStateOf(2)
        val other = mutableStateOf(5)

        val mapped = memo { source() * 2 }
        val zipped = memo { source() to other() }

        assertEquals(4, mapped.value)
        assertEquals(2 to 5, zipped.value)

        val holder = mutableStateOf<dev.deftu.stateful.State<Int>>(source)
        val followed = memo { holder()() }

        assertEquals(2, followed.value)

        holder.set(other)
        assertEquals(5, followed.value)
    }

    @Test
    fun anOwnerCreatedForAHostOutlivesTheCallThatMadeIt() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createOwner(Scheduler.Immediate)
        runWithOwner(owner) { effect { observed.add(source()) } }

        source.set(1)
        assertEquals(listOf(0, 1), observed)

        owner.dispose()
    }
}
