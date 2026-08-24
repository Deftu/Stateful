import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.deftu.stateful.dsl.runWithOwner

class OwnershipTest {
    @Test
    fun disposingARootStopsEveryEffectUnderIt() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(source()) }
            effect { observed.add(source() * 100) }
            owner
        }

        source.set(1)
        owner.dispose()
        source.set(2)

        assertTrue(owner.isDisposed)
        assertEquals(listOf(0, 0, 1, 100), observed)
    }

    @Test
    fun cleanupsRunInReverseCreationOrder() {
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
    fun disposalIsDepthFirstWithChildrenBeforeTheirParent() {
        val order = mutableListOf<String>()
        val source = mutableStateOf(0)

        val owner = createRoot { owner ->
            onCleanup { order.add("root") }
            effect {
                source()
                onCleanup { order.add("effect") }
            }
            owner
        }

        owner.dispose()

        assertEquals(listOf("effect", "root"), order)
    }

    @Test
    fun anEffectCleanupRunsBeforeEachRerun() {
        val order = mutableListOf<String>()
        val source = mutableStateOf(0)

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
    fun anEffectNestedInAnEffectDiesWithTheOuterRerun() {
        val outerSource = mutableStateOf(0)
        val innerSource = mutableStateOf(0)
        val observed = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect {
                val outer = outerSource()
                effect { observed.add("inner ${outer}:${innerSource()}") }
            }
            owner
        }

        innerSource.set(1)
        outerSource.set(1)
        observed.clear()
        innerSource.set(2)
        owner.dispose()

        assertEquals(listOf("inner 1:2"), observed)
    }

    @Test
    fun aNestedRootIsDetachedFromTheOuterRoot() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        lateinit var inner: dev.deftu.stateful.Disposable
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
    fun disposingARootTwiceIsHarmless() {
        val order = mutableListOf<String>()
        val owner = createRoot { owner ->
            onCleanup { order.add("cleanup") }
            owner
        }

        owner.dispose()
        owner.dispose()

        assertEquals(listOf("cleanup"), order)
    }

    @Test
    fun aSchedulerReceivesEveryEffectBodyUnderItsRoot() {
        val queued = mutableListOf<() -> Unit>()
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createRoot(scheduler = { task -> queued.add(task) }) { owner ->
            effect { observed.add(source()) }
            owner
        }

        assertEquals(1, queued.size)
        assertEquals(emptyList<Int>(), observed)

        queued.removeFirst().invoke()
        assertEquals(listOf(0), observed)

        source.set(1)
        assertEquals(1, queued.size)

        queued.removeFirst().invoke()
        assertEquals(listOf(0, 1), observed)

        owner.dispose()
    }

    @Test
    fun theImmediateSchedulerRunsBodiesBeforeTheWriteReturns() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createRoot(scheduler = Scheduler.Immediate) { owner ->
            effect { observed.add(source()) }
            owner
        }

        source.set(1)
        assertEquals(listOf(0, 1), observed)

        owner.dispose()
    }

    @Test
    fun aDerivedStateStopsFollowingItsSourceOnceItsOwnerIsDisposed() {
        val source = mutableStateOf(1)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(source() * 2) }
            owner
        }

        source.set(2)
        owner.dispose()
        source.set(50)

        assertEquals(listOf(2, 4), observed)
    }

    @Test
    fun anUnscopedRootAdoptsComputationsFromLaterCallbacks() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner()
        runWithOwner(root) { effect { observed.add(source()) } }
        source.set(1)
        runWithOwner(root) { effect { observed.add(source() * 100) } }
        source.set(2)

        root.dispose()
        source.set(3)

        assertEquals(listOf(0, 1, 100, 2, 200), observed)
    }

    @Test
    fun runWithOwnerBorrowsTheLifetimeRatherThanCreatingOne() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner()
        runWithOwner(root) { effect { observed.add(source()) } }

        source.set(1)
        assertEquals(listOf(0, 1), observed)

        root.dispose()
        source.set(2)
        assertEquals(listOf(0, 1), observed)
    }

    @Test
    fun anUnscopedRootPassesItsSchedulerToLaterComputations() {
        val queued = mutableListOf<() -> Unit>()
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner(scheduler = { task -> queued.add(task) })
        runWithOwner(root) { effect { observed.add(source()) } }

        assertEquals(1, queued.size)
        queued.removeFirst().invoke()
        assertEquals(listOf(0), observed)

        root.dispose()
    }
}
