import dev.deftu.stateful.Disposable
import dev.deftu.stateful.Equality
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.State
import dev.deftu.stateful.StateWarnings
import dev.deftu.stateful.collections.ListChange
import dev.deftu.stateful.collections.mapKeyed
import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.collections.reactiveMapOf
import dev.deftu.stateful.collections.reactiveSetOf
import dev.deftu.stateful.combine
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.combineStateOf
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.derivedStateOf
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.flatMappedStateOf
import dev.deftu.stateful.dsl.mappedStateOf
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateBound
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.rawStateOf
import dev.deftu.stateful.dsl.runWithOwner
import dev.deftu.stateful.dsl.stateBound
import dev.deftu.stateful.dsl.stateOf
import dev.deftu.stateful.dsl.trackedStateBound
import dev.deftu.stateful.dsl.untracked
import dev.deftu.stateful.dsl.zippedStateOf
import dev.deftu.stateful.ext.and
import dev.deftu.stateful.ext.component1
import dev.deftu.stateful.ext.component2
import dev.deftu.stateful.ext.component3
import dev.deftu.stateful.ext.contains
import dev.deftu.stateful.ext.endsWith
import dev.deftu.stateful.ext.inverted
import dev.deftu.stateful.ext.isBlank
import dev.deftu.stateful.ext.isEmpty
import dev.deftu.stateful.ext.isNotBlank
import dev.deftu.stateful.ext.isNotEmpty
import dev.deftu.stateful.ext.first
import dev.deftu.stateful.ext.length
import dev.deftu.stateful.ext.lowercase
import dev.deftu.stateful.ext.startsWith
import dev.deftu.stateful.ext.trim
import dev.deftu.stateful.ext.uppercase
import dev.deftu.stateful.ext.nand
import dev.deftu.stateful.ext.nor
import dev.deftu.stateful.ext.not
import dev.deftu.stateful.ext.or
import dev.deftu.stateful.ext.second
import dev.deftu.stateful.ext.third
import dev.deftu.stateful.ext.toggle
import dev.deftu.stateful.ext.xor
import dev.deftu.stateful.flatMap
import dev.deftu.stateful.getOrDefault
import dev.deftu.stateful.getOrElse
import dev.deftu.stateful.getOrThrow
import dev.deftu.stateful.map
import dev.deftu.stateful.snapshot
import dev.deftu.stateful.zip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Mirrors every sample in `docs/` that depends only on the core library, asserting what the
 * surrounding prose claims rather than only that the code compiles.
 */
class DocsSamplesTest {
    private data class Row(val id: Int, val label: String)

    private class View {
        var title: String = ""
        var subtitle: String = ""
        var badge: String = ""
    }

    private class RowView {
        var label: String = ""
        var position: Int = 0
    }

    private class Item(val price: Int)

    private class Cart {
        private val items = reactiveListOf<Item>()

        fun add(item: Item) {
            items.add(item)
        }

        fun subtotal(): Int = items.sumOf { it.price }
    }

    private class Node(val emit: () -> String)

    private class Document(private val nodes: List<Node>) {
        fun render(): String = nodes.joinToString("") { node -> node.emit() }
    }

    private fun search(query: String): List<String> = listOf("$query:1")

    // --- README.md ---

    @Test
    fun theIndexSummarySamplePrintsBothTemperatures() {
        val printed = mutableListOf<String>()
        val celsius = mutableStateOf(20)
        val fahrenheit = memo { celsius() * 9 / 5 + 32 }

        val root = createRoot { owner ->
            effect { printed.add("${fahrenheit()}F") }
            owner
        }

        celsius.set(100)
        root.dispose()
        celsius.set(0)

        assertEquals(listOf("68F", "212F"), printed)
    }

    // --- getting-started.md ---

    @Test
    fun theFirstStateSampleEndsAtTwo() {
        val count = mutableStateOf(0)

        count.set(1)
        count.update { it + 1 }

        assertEquals(2, count.value)
    }

    @Test
    fun theFirstDerivationSampleConvertsTemperatures() {
        val celsius = mutableStateOf(20)
        val fahrenheit = memo { celsius() * 9 / 5 + 32 }

        assertEquals(68, fahrenheit.value)

        celsius.set(100)

        assertEquals(212, fahrenheit.value)
    }

    @Test
    fun theFirstEffectSampleStopsWhenTheRootIsDisposed() {
        val printed = mutableListOf<String>()
        val name = mutableStateOf("world")

        val root = createRoot { owner ->
            effect { printed.add("hello, ${name()}") }
            owner
        }

        name.set("there")
        root.dispose()
        name.set("ignored")

        assertEquals(listOf("hello, world", "hello, there"), printed)
    }

    @Test
    fun theOneRuleSampleTracksOnlyTheFunctionCall() {
        val name = mutableStateOf("world")

        val greeting = memo { "hello, ${name()}" }
        val atStartup = name.value

        name.set("there")

        assertEquals("hello, there", greeting.value)
        assertEquals("world", atStartup)
    }

    // --- concepts.md ---

    @Test
    fun theDiamondSamplePrintsFiveThenTenAndNothingBetween() {
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
        owner.dispose()
    }

    @Test
    fun theUnobservedMemoSampleNeverComputes() {
        var computations = 0
        val source = mutableStateOf(1)
        memo {
            computations++
            source() * 2
        }

        source.set(2)
        source.set(3)

        assertEquals(0, computations)
    }

    @Test
    fun theEqualityCutoffSampleLeavesDownstreamAtOne() {
        val source = mutableStateOf(1)
        val parity = memo { source() % 2 }

        var downstream = 0
        val derived = memo {
            downstream++
            parity()
        }

        assertEquals(1, derived.value)
        assertEquals(1, downstream)

        source.set(3)

        assertEquals(1, derived.value)
        assertEquals(1, downstream)
    }

    @Test
    fun theUntrackedBlockSampleGoesStaleAndThenCatchesUp() {
        val tracked = mutableStateOf(1)
        val ignored = mutableStateOf(100)

        var runs = 0
        val combined = memo {
            runs++
            tracked() + untracked { ignored() }
        }

        assertEquals(101, combined.value)
        assertEquals(1, runs)

        ignored.set(200)

        assertEquals(101, combined.value)
        assertEquals(1, runs)

        tracked.set(2)

        assertEquals(202, combined.value)
        assertEquals(2, runs)
    }

    @Test
    fun theForeignCallbackSampleStillRegistersTheRead() {
        val name = mutableStateOf("world")
        val document = Document(listOf(Node { "hello, ${name()}" }))
        val rendered = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { rendered.add(document.render()) }
            owner
        }

        name.set("there")

        assertEquals(listOf("hello, world", "hello, there"), rendered)
        owner.dispose()
    }

    @Test
    fun theMemoAgainstDerivedStateOfSampleShowsTheMissingCutoff() {
        val source = mutableStateOf(1)
        val cached = memo { source() % 2 }
        val uncached = derivedStateOf { source() % 2 }

        val fromDerived = mutableListOf<Int>()
        val fromMemo = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { fromDerived.add(uncached()) }
            effect { fromMemo.add(cached()) }
            owner
        }

        source.set(3)

        assertEquals(listOf(1, 1), fromDerived)
        assertEquals(listOf(1), fromMemo)
        owner.dispose()
    }

    @Test
    fun theConditionalDependencySampleSubscribesOnlyToTheBranchItTook() {
        val condition = mutableStateOf(true)
        val left = mutableStateOf("left")
        val right = mutableStateOf("right")

        var runs = 0
        val chosen = memo {
            runs++
            if (condition()) left() else right()
        }

        assertEquals("left", chosen.value)
        assertEquals(1, runs)

        right.set("changed")

        assertEquals("left", chosen.value)
        assertEquals(1, runs)

        condition.set(false)

        assertEquals("changed", chosen.value)
        assertEquals(2, runs)

        left.set("ignored")

        assertEquals("changed", chosen.value)
        assertEquals(2, runs)
    }

    @Test
    fun theSubscribeSampleFiresOnChangeOnlyAndStopsWhenDisposed() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val subscription = source.subscribe { value -> observed.add(value) }
        source.set(1)
        source.set(2)
        subscription.dispose()
        source.set(3)

        assertEquals(listOf(1, 2), observed)
    }

    @Test
    fun theCustomEqualitySampleOnlyFiresWhenParityFlips() {
        val parity = mutableStateOf(1) { a, b -> a % 2 == b % 2 }
        val observed = mutableListOf<Int>()
        parity.subscribe { value -> observed.add(value) }

        parity.set(3)
        parity.set(5)
        parity.set(2)

        assertEquals(listOf(2), observed)
    }

    @Test
    fun theRawStateSampleReactsToReplacementRatherThanValue() {
        val first = listOf("a")
        val second = listOf("a")

        val raw = rawStateOf(first)
        val fromRaw = mutableListOf<List<String>>()
        raw.subscribe { value -> fromRaw.add(value) }

        raw.set(second)
        raw.set(second)

        assertEquals(listOf(second), fromRaw)

        val ordinary = mutableStateOf(first)
        val fromOrdinary = mutableListOf<List<String>>()
        ordinary.subscribe { value -> fromOrdinary.add(value) }

        ordinary.set(second)

        assertTrue(fromOrdinary.isEmpty())
    }

    @Test
    fun theConstantStateSampleNeverChanges() {
        val constant = stateOf(7)

        assertEquals(7, constant.value)
        assertEquals(7, constant())
    }

    @Test
    fun theIdentitySampleComparesStatesByIdentityAndValuesByValue() {
        assertNotEquals(stateOf("a"), stateOf("a"))
        assertEquals(stateOf("a").value, stateOf("a").value)
    }

    // --- operators.md ---

    @Test
    fun theCombiningSampleDerivesEachShape() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(10)

        val doubled = first.map { it * 2 }
        val paired = first.zip(second)
        val summed = first.combine(second) { a, b -> a + b }

        assertEquals(2, doubled.value)
        assertEquals(1 to 10, paired.value)
        assertEquals(11, summed.value)

        first.set(2)

        assertEquals(4, doubled.value)
        assertEquals(2 to 10, paired.value)
        assertEquals(12, summed.value)
    }

    @Test
    fun theFlatMapSampleFollowsTheInnerStateAndDropsTheOldOne() {
        val a = mutableStateOf("from a")
        val b = mutableStateOf("from b")
        val which = mutableStateOf(a)

        val current: State<String> = which.flatMap { it }

        assertEquals("from a", current.value)

        a.set("edited")

        assertEquals("edited", current.value)

        which.set(b)

        assertEquals("from b", current.value)

        a.set("ignored")

        assertEquals("from b", current.value)
    }

    @Test
    fun theFactoryFormsSampleMatchesTheReceiverForms() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(10)
        val third = mutableStateOf(100)
        val inner = mutableStateOf("inner")
        val outer = mutableStateOf(inner)

        assertEquals(2, mappedStateOf(first) { it * 2 }.value)
        assertEquals("inner", flatMappedStateOf(outer) { it }.value)
        assertEquals(1 to 10, zippedStateOf(first, second).value)
        assertEquals(Triple(1, 10, 100), zippedStateOf(first, second, third).value)
        assertEquals(11, combineStateOf(first, second) { a, b -> a + b }.value)
        assertEquals(111, combineStateOf(first, second, third) { a, b, c -> a + b + c }.value)
    }

    @Test
    fun theNullableReadsSampleUnwrapsAndTracks() {
        val name = mutableStateOf<String?>(null)

        assertEquals("anonymous", name.getOrDefault("anonymous"))
        assertEquals("anonymous", name.getOrElse { "anonymous" })
        assertFailsWith<IllegalStateException> { name.getOrThrow() }
        assertFailsWith<IllegalStateException> { name.getOrThrow("name was never set") }
        assertFailsWith<IllegalArgumentException> { name.getOrThrow(IllegalArgumentException()) }
        assertFailsWith<IllegalArgumentException> { name.getOrThrow { IllegalArgumentException() } }

        var runs = 0
        val display = memo {
            runs++
            name.getOrDefault("anonymous")
        }

        assertEquals("anonymous", display.value)
        assertEquals(1, runs)

        name.set("ada")

        assertEquals("ada", display.value)
        assertEquals(2, runs)
    }

    @Test
    fun theBooleanOperatorsSampleAnswersTheTruthTable() {
        val enabled = mutableStateOf(false)
        val visible = mutableStateOf(true)

        enabled.toggle()

        assertTrue(enabled.value)
        assertFalse(enabled.inverted().value)
        assertFalse((!enabled).value)
        assertTrue((enabled and visible).value)
        assertTrue((enabled or visible).value)
        assertFalse((enabled xor visible).value)
        assertFalse((enabled nand visible).value)
        assertFalse((enabled nor visible).value)
        assertTrue((enabled and true).value)
        assertTrue((true and enabled).value)
    }

    @Test
    fun theStringOperatorsSampleDerivesEachShape() {
        val query = mutableStateOf(" Error: 42! ")

        assertEquals(12, query.length().value)
        assertFalse(query.isEmpty().value)
        assertTrue(query.isNotEmpty().value)
        assertFalse(query.isBlank().value)
        assertTrue(query.isNotBlank().value)
        assertEquals("Error: 42!", query.trim().value)
        assertEquals(" error: 42! ", query.lowercase().value)
        assertEquals(" ERROR: 42! ", query.uppercase().value)
        assertTrue(query.trim().startsWith("Error").value)
        assertTrue(query.trim().endsWith("!").value)
        assertTrue(query.contains("error", ignoreCase = true).value)

        val needle = mutableStateOf("42")

        assertTrue(query.contains(needle).value)
        assertFalse(query.startsWith(needle).value)
        assertFalse(query.endsWith(needle).value)
    }

    @Test
    fun theTrackedDelegateClassSampleSubscribesOnEveryPropertyRead() {
        val source = mutableStateOf("first")

        class Header(state: State<String>) {
            val title: String by trackedStateBound(state)
        }

        val header = Header(source)
        var runs = 0
        val rendered = memo {
            runs++
            header.title
        }

        assertEquals("first", rendered.value)
        assertEquals(1, runs)

        source.set("second")

        assertEquals("second", rendered.value)
        assertEquals(2, runs)
    }

    @Test
    fun theStringCutoffSampleRedrawsOnlyWhenTheAnswerFlips() {
        val query = mutableStateOf("")
        val submittable = query.isNotBlank()

        var redraws = 0
        val owner = createRoot { owner ->
            effect {
                submittable()
                redraws++
            }
            owner
        }
        redraws = 0

        query.set("h")
        assertEquals(1, redraws)

        query.set("he")
        assertEquals(1, redraws)

        query.set("hel")
        assertEquals(1, redraws)

        query.set("")
        assertEquals(2, redraws)

        assertEquals(0, query.length().value)
        owner.dispose()
    }

    @Test
    fun thePairSampleSplitsComponentsWithTheirOwnCutoffs() {
        val point = mutableStateOf(1 to 2)

        val x = point.first()
        val y = point.second()

        assertEquals(1, x.value)
        assertEquals(2, y.value)

        val (left, right) = point
        assertEquals(1, left.value)
        assertEquals(2, right.value)

        var xRuns = 0
        val owner = createRoot { owner ->
            effect {
                x()
                xRuns++
            }
            owner
        }
        xRuns = 0

        point.set(1 to 3)

        assertEquals(0, xRuns)
        assertEquals(3, y.value)
        owner.dispose()
    }

    @Test
    fun theTripleSampleSplitsAllThreeComponents() {
        val triple = mutableStateOf(Triple(1, "two", true))

        assertEquals(1, triple.first().value)
        assertEquals("two", triple.second().value)
        assertEquals(true, triple.third().value)

        val (a, b, c) = triple

        assertEquals(1, a.value)
        assertEquals("two", b.value)
        assertEquals(true, c.value)
    }

    @Test
    fun theDelegateSampleReadsUntrackedUnlessItSaysOtherwise() {
        val source = mutableStateOf(1)

        val plain = object {
            val value: Int by stateBound(source)
        }
        val tracking = object {
            val value: Int by trackedStateBound(source)
        }

        var plainRuns = 0
        val fromPlain = memo {
            plainRuns++
            plain.value
        }

        var trackedRuns = 0
        val fromTracked = memo {
            trackedRuns++
            tracking.value
        }

        assertEquals(1, fromPlain.value)
        assertEquals(1, fromTracked.value)

        source.set(2)

        assertEquals(1, fromPlain.value)
        assertEquals(1, plainRuns)
        assertEquals(2, fromTracked.value)
        assertEquals(2, trackedRuns)

        var writable: Int by mutableStateBound(source)
        writable = 5

        assertEquals(5, source.value)
        assertEquals(5, writable)
    }

    @Test
    fun theSnapshotSampleDetachesTheCollection() {
        val backing = mutableListOf("a", "b")
        val source = mutableStateOf<List<String>>(backing)

        val taken = snapshot(source)
        backing.add("c")

        assertEquals(listOf("a", "b"), taken)
        assertNotSame(backing, taken)
    }

    // --- lifetimes.md ---

    @Test
    fun theCreateRootSampleTearsTheEffectDownOnDispose() {
        val view = View()
        val title = mutableStateOf("first")
        var detached = false

        val owner = createRoot { owner ->
            effect { view.title = title() }
            onCleanup { detached = true }
            owner
        }

        title.set("second")

        assertEquals("second", view.title)

        owner.dispose()
        title.set("third")

        assertEquals("second", view.title)
        assertTrue(detached)
    }

    @Test
    fun theCleanupOrderSampleUnwindsInReverse() {
        val order = mutableListOf<String>()

        val owner = createRoot { owner ->
            onCleanup { order.add("first") }
            onCleanup { order.add("second") }
            onCleanup { order.add("third") }
            owner
        }

        owner.dispose()

        assertEquals(listOf("third", "second", "first"), order)
    }

    @Test
    fun theCallbackShapedHostSampleMountsAndUnmounts() {
        val view = View()
        val title = mutableStateOf("first")
        var detached = false

        class Screen {
            private val root = createOwner()

            fun mount() = runWithOwner(root) {
                effect { view.title = title() }
                onCleanup { detached = true }
            }

            fun unmount() = root.dispose()
        }

        val screen = Screen()
        screen.mount()

        title.set("second")

        assertEquals("second", view.title)

        screen.unmount()
        title.set("third")

        assertEquals("second", view.title)
        assertTrue(detached)
    }

    @Test
    fun thePerRequestRootSampleDisposesEachRequest() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        fun handle() {
            val root = createOwner()
            try {
                runWithOwner(root) { effect { observed.add(source()) } }
            } finally {
                root.dispose()
            }
        }

        handle()
        handle()
        source.set(1)

        assertEquals(listOf(0, 0), observed)
    }

    @Test
    fun theEffectCleanupSampleRunsBeforeEachRerun() {
        val source = mutableStateOf(0)
        val order = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect {
                val value = source()
                order.add("run $value")
                onCleanup { order.add("cleanup $value") }
            }
            owner
        }

        source.set(1)
        source.set(2)
        owner.dispose()

        assertEquals(
            listOf("run 0", "cleanup 0", "run 1", "cleanup 1", "run 2", "cleanup 2"),
            order,
        )
    }

    @Test
    fun theNestedEffectSampleDiesWithTheOuterRerun() {
        val outerSource = mutableStateOf(0)
        val innerSource = mutableStateOf(0)
        val observed = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect {
                val outer = outerSource()
                effect { observed.add("inner $outer:${innerSource()}") }
            }
            owner
        }

        assertEquals(listOf("inner 0:0"), observed)

        innerSource.set(1)

        assertEquals(listOf("inner 0:0", "inner 0:1"), observed)

        outerSource.set(1)

        assertEquals(listOf("inner 0:0", "inner 0:1", "inner 1:1"), observed)

        observed.clear()
        innerSource.set(2)
        owner.dispose()

        assertEquals(listOf("inner 1:2"), observed)
    }

    @Test
    fun theNestedRootSampleIsDetachedFromTheOuterRoot() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        lateinit var inner: Disposable
        val outer = createRoot { outer ->
            inner = createRoot { nested ->
                effect { observed.add(source()) }
                nested
            }
            outer
        }

        outer.dispose()
        source.set(1)

        assertEquals(listOf(0, 1), observed)

        inner.dispose()
        source.set(2)

        assertEquals(listOf(0, 1), observed)
    }

    @Test
    fun theOrphanWarningSampleReportsThroughTheHandler() {
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
    fun theOwnerIntrospectionSampleCountsWhatItHolds() {
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

    // --- scheduling.md ---

    @Test
    fun theImmediateSchedulerSampleRunsBeforeTheWriteReturns() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createRoot(Scheduler.Immediate) { owner ->
            effect { observed.add(source()) }
            owner
        }

        source.set(1)

        assertEquals(listOf(0, 1), observed)
        owner.dispose()
    }

    @Test
    fun theQueuedSchedulerSampleHoldsAndCoalesces() {
        val scheduler = Scheduler.queued()
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner(scheduler)
        runWithOwner(root) { effect { observed.add(source()) } }

        assertEquals(1, scheduler.size)
        assertEquals(emptyList<Int>(), observed)

        scheduler.drain()

        assertEquals(listOf(0), observed)

        source.set(1)
        source.set(2)

        assertEquals(listOf(0), observed)
        assertEquals(1, scheduler.size)

        scheduler.drain()

        assertEquals(listOf(0, 2), observed)
        root.dispose()
    }

    @Test
    fun theDrainSampleRunsTasksQueuedDuringTheDrain() {
        val scheduler = Scheduler.queued()
        val first = mutableStateOf(0)
        val second = mutableStateOf(0)
        val observed = mutableListOf<String>()

        val root = createOwner(scheduler)
        runWithOwner(root) {
            effect { second.set(first() + 1) }
            effect { observed.add("second=${second()}") }
        }
        scheduler.drain()
        observed.clear()

        first.set(10)
        scheduler.drain()

        assertEquals(listOf("second=11"), observed)
        assertEquals(0, scheduler.size)
        root.dispose()
    }

    @Test
    fun theCustomSchedulerSampleDrainsOnItsOwnLoop() {
        val queue = ArrayDeque<() -> Unit>()
        val onMyLoop = Scheduler { task -> queue.addLast(task) }

        val frame = mutableStateOf(0)
        val rendered = mutableListOf<Int>()

        val root = createOwner(onMyLoop)
        runWithOwner(root) { effect { rendered.add(frame()) } }

        assertEquals(emptyList<Int>(), rendered)

        while (queue.isNotEmpty()) queue.removeFirst().invoke()

        assertEquals(listOf(0), rendered)
        root.dispose()
    }

    @Test
    fun theBatchSampleRunsTheEffectOnce() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(first() + second()) }
            owner
        }

        batch {
            first.set(10)
            second.set(20)
        }

        assertEquals(listOf(3, 30), observed)
        owner.dispose()
    }

    @Test
    fun theBatchReadSampleSeesPendingWrites() {
        val source = mutableStateOf(1)
        val doubled = memo { source() * 2 }
        val seen = mutableListOf<Int>()

        batch {
            source.set(5)
            seen.add(source.value)
            seen.add(doubled.value)
            source.set(7)
            seen.add(doubled.value)
        }

        assertEquals(listOf(5, 10, 14), seen)
    }

    @Test
    fun theNetZeroBatchSampleIsAbsorbedByAMemoButNotByADirectReader() {
        val source = mutableStateOf(1)
        val doubled = memo { source() * 2 }
        val direct = mutableListOf<Int>()
        val throughMemo = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { direct.add(source()) }
            effect { throughMemo.add(doubled()) }
            owner
        }
        direct.clear()
        throughMemo.clear()

        batch {
            source.set(2)
            source.set(1)
        }

        assertEquals(listOf(1), direct)
        assertEquals(emptyList<Int>(), throughMemo)
        owner.dispose()
    }

    // --- collections.md ---

    @Test
    fun thePerSlotSampleWakesOnlyTheAffectedConsumer() {
        val list = reactiveListOf("a", "b", "c")
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { first.add(list[0]) }
            effect { second.add(list[1]) }
            owner
        }

        list[1] = "B"

        assertEquals(listOf("a"), first)
        assertEquals(listOf("b", "B"), second)
        owner.dispose()
    }

    @Test
    fun theStructureSignalSampleIgnoresReplacementAndNoticesAppends() {
        val list = reactiveListOf("a")
        val sizes = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { sizes.add(list.size) }
            owner
        }

        list[0] = "A"

        assertEquals(listOf(1), sizes)

        list.add("b")

        assertEquals(listOf(1, 2), sizes)
        owner.dispose()
    }

    @Test
    fun theCoarseEdgeSampleWakesEveryPositionalReaderOnAStructuralEdit() {
        val list = reactiveListOf("a", "b")
        val first = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { first.add(list[0]) }
            owner
        }

        list.add("c")

        assertEquals(listOf("a", "a"), first)

        list.add(0, "z")

        assertEquals(listOf("a", "a", "z"), first)
        owner.dispose()
    }

    @Test
    fun theWholeListReadSampleTracksEveryElement() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
        val labels = memo { list.joinToString { it.label } }

        assertEquals("one, two", labels.value)

        list[0] = Row(1, "ONE")

        assertEquals("ONE, two", labels.value)
    }

    @Test
    fun theUntrackedCollectionReadsSampleRegistersNothing() {
        val list = reactiveListOf("a", "b")
        val map = reactiveMapOf("key" to 1)
        val set = reactiveSetOf("element")

        assertEquals("a", list.getUntracked(0))
        assertEquals(2, list.untrackedSize)
        assertEquals(1, map.getUntracked("key"))
        assertEquals(1, map.untrackedSize)
        assertTrue(set.containsUntracked("element"))
        assertEquals(1, set.untrackedSize)

        var runs = 0
        val derived = memo {
            runs++
            list.getUntracked(0)
        }

        assertEquals("a", derived.value)

        list[0] = "A"

        assertEquals("a", derived.value)
        assertEquals(1, runs)
    }

    @Test
    fun theChangesSampleEmitsASetCarryingThePreviousValue() {
        val list = reactiveListOf("a", "b")
        val seen = mutableListOf<ListChange<String>>()

        val owner = createRoot { owner ->
            effect { seen.addAll(list.changes()) }
            owner
        }
        seen.clear()

        list[1] = "B"

        assertEquals(listOf<ListChange<String>>(ListChange.Set(1, "b", "B")), seen)
        owner.dispose()
    }

    @Test
    fun theMoveSampleReordersWithoutRemovingAndInserting() {
        val list = reactiveListOf("a", "b", "c")
        val seen = mutableListOf<ListChange<String>>()

        val owner = createRoot { owner ->
            effect { seen.addAll(list.changes()) }
            owner
        }
        seen.clear()

        list.move(0, 2)

        assertEquals(listOf("b", "c", "a"), list.toList())
        assertEquals(listOf<ListChange<String>>(ListChange.Move(0, 2, "a")), seen)
        owner.dispose()
    }

    @Test
    fun theAbsentKeySampleWakesOnItsLaterInsert() {
        val map = reactiveMapOf<String, Int>()
        val observed = mutableListOf<Int?>()

        val owner = createRoot { owner ->
            effect { observed.add(map["pending"]) }
            owner
        }

        map["pending"] = 7

        assertEquals(listOf(null, 7), observed)
        owner.dispose()
    }

    @Test
    fun theSetMembershipSampleIsIndependentPerElement() {
        val set = reactiveSetOf("a")
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()

        val owner = createRoot { owner ->
            effect { first.add("a" in set) }
            effect { second.add("b" in set) }
            owner
        }

        set.add("b")

        assertEquals(listOf(true), first)
        assertEquals(listOf(false, true), second)
        owner.dispose()
    }

    @Test
    fun theMapKeyedSampleMovesResultsRatherThanRebuildingThem() {
        val rows = reactiveListOf(Row(1, "one"), Row(2, "two"), Row(3, "three"))
        var builds = 0

        lateinit var labels: State<List<String>>
        val owner = createRoot { owner ->
            labels = rows.mapKeyed(key = { it.id }) { element, _ ->
                builds++
                element.value.label
            }
            owner
        }

        assertEquals(3, builds)

        rows.move(0, 2)

        assertEquals(3, builds)
        assertEquals(listOf("two", "three", "one"), labels.value)
        owner.dispose()
    }

    @Test
    fun theMapKeyedRowSampleFollowsBothElementAndIndex() {
        val todos = reactiveListOf(Row(1, "write"), Row(2, "test"))

        lateinit var rows: State<List<RowView>>
        val owner = createRoot { owner ->
            rows = todos.mapKeyed(key = { it.id }) { todo, index ->
                RowView().also { row ->
                    effect { row.label = todo().label }
                    effect { row.position = index() }
                }
            }
            owner
        }

        assertEquals(listOf("write", "test"), rows.value.map { it.label })
        assertEquals(listOf(0, 1), rows.value.map { it.position })

        val original = rows.value
        todos.move(0, 1)

        assertEquals(listOf("test", "write"), rows.value.map { it.label })
        assertEquals(listOf(1, 0), original.map { it.position })

        todos[0] = Row(2, "tested")

        assertEquals("tested", original[1].label)
        owner.dispose()
    }

    @Test
    fun theMapKeyedCleanupSampleDisposesADepartedElement() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
        val cleaned = mutableListOf<Int>()

        val owner = createRoot { owner ->
            list.mapKeyed(key = { it.id }) { element, _ ->
                val id = element.value.id
                onCleanup { cleaned.add(id) }
            }
            owner
        }

        list.removeAt(0)

        assertEquals(listOf(1), cleaned)

        owner.dispose()

        assertEquals(listOf(1, 2), cleaned)
    }

    // --- concurrency.md ---

    @Test
    fun theDiamondSampleAlwaysReadsAMultipleOfFive() {
        val root = mutableStateOf(1)
        val doubled = memo { root() * 2 }
        val tripled = memo { root() * 3 }
        val sum = memo { doubled() + tripled() }

        repeat(20) { round ->
            root.set(round + 1)
            assertEquals(0, sum.value % 5)
        }
    }

    @Test
    fun theAsynchronousWorkSampleWritesBackIntoASource() {
        val query = mutableStateOf("kotlin")
        val results = mutableStateOf<List<String>>(emptyList())

        val owner = createRoot { owner ->
            effect {
                val current = query()
                results.set(search(current))
            }
            owner
        }

        assertEquals(listOf("kotlin:1"), results.value)

        query.set("multiplatform")

        assertEquals(listOf("multiplatform:1"), results.value)
        owner.dispose()
    }

    // --- testing.md ---

    @Test
    fun theCartTestSampleObservesEverySubtotal() {
        val root = createOwner()
        try {
            runWithOwner(root) {
                val cart = Cart()
                val observed = mutableListOf<Int>()
                effect { observed.add(cart.subtotal()) }

                cart.add(Item(price = 5))
                cart.add(Item(price = 3))

                assertEquals(listOf(0, 5, 8), observed)
            }
        } finally {
            root.dispose()
        }
    }

    @Test
    fun theDerivedStateTestSampleNeedsNoOwner() {
        val celsius = mutableStateOf(20)
        val fahrenheit = memo { celsius() * 9 / 5 + 32 }

        assertEquals(68, fahrenheit.value)

        celsius.set(100)

        assertEquals(212, fahrenheit.value)
    }

    @Test
    fun theInjectedSchedulerSampleAssertsAtEachDrain() {
        val scheduler = Scheduler.queued()
        val root = createOwner(scheduler)

        val cart = Cart()
        val observed = mutableListOf<Int>()
        runWithOwner(root) { effect { observed.add(cart.subtotal()) } }

        scheduler.drain()
        assertEquals(listOf(0), observed)

        cart.add(Item(price = 5))
        scheduler.drain()
        assertEquals(listOf(0, 5), observed)

        root.dispose()
    }

    @Test
    fun theLeakAssertionSampleCountsComputations() {
        val view = View()
        val title = mutableStateOf("a")
        val subtitle = mutableStateOf("b")
        val badge = mutableStateOf("c")

        val owner = createOwner()
        runWithOwner(owner) {
            effect { view.title = title() }
            effect { view.subtitle = subtitle() }
            effect { view.badge = badge() }
        }

        assertEquals(3, owner.computationCount)

        owner.dispose()

        assertTrue(owner.isEmpty)
    }

    @Test
    fun theMemoRunCountingSampleStartsAtZero() {
        val source = mutableStateOf(1)
        var runs = 0
        val doubled = memo {
            runs++
            source() * 2
        }

        assertEquals(0, runs)

        assertEquals(2, doubled.value)
        assertEquals(1, runs)
    }

    @Test
    fun theEqualityCompanionSampleNamesTheThreeComparisons() {
        val structural: Equality<Int> = Equality.structural()
        val referential: Equality<List<String>> = Equality.referential()
        val never: Equality<Int> = Equality.never()

        val value = listOf("a")

        assertTrue(structural.areEqual(1, 1))
        assertTrue(referential.areEqual(value, value))
        assertFalse(referential.areEqual(listOf("a"), listOf("a")))
        assertFalse(never.areEqual(1, 1))
    }

    @Test
    fun theOwnerTypeIsWhatCreateRootHandsBack() {
        val owner: Owner = createRoot { it }

        assertTrue(owner.isEmpty)
        assertFalse(owner.isDisposed)

        owner.dispose()

        assertTrue(owner.isDisposed)
    }
}
