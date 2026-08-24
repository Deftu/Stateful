import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BatchTest {
    @Test
    fun anEffectRunsOncePerBatchRatherThanOncePerWrite() {
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
    fun readsInsideABatchSeePendingWrites() {
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
    fun onlyTheOutermostBatchFlushes() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(source()) }
            owner
        }
        observed.clear()

        batch {
            source.set(1)
            batch {
                source.set(2)
                batch { source.set(3) }
                assertEquals(emptyList<Int>(), observed)
            }
            assertEquals(emptyList<Int>(), observed)
        }

        assertEquals(listOf(3), observed)
        owner.dispose()
    }

    @Test
    fun aBatchThatWritesTheValueBackStillWakesADirectEffect() {
        val source = mutableStateOf(1)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(source()) }
            owner
        }
        observed.clear()

        batch {
            source.set(2)
            source.set(1)
        }

        assertEquals(listOf(1), observed)
        owner.dispose()
    }

    @Test
    fun aMemoAbsorbsANetZeroChangeThatADirectEffectCannot() {
        val source = mutableStateOf(1)
        val doubled = memo { source() * 2 }
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(doubled()) }
            owner
        }
        observed.clear()

        batch {
            source.set(2)
            source.set(1)
        }

        assertEquals(emptyList<Int>(), observed)
        owner.dispose()
    }

    @Test
    fun aBatchReturnsTheValueOfItsBlock() {
        val source = mutableStateOf(2)
        val result = batch {
            source.set(3)
            source.value * 10
        }

        assertEquals(30, result)
    }

    @Test
    fun aThrowingBatchStillFlushes() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(source()) }
            owner
        }
        observed.clear()

        assertFailsWith<IllegalStateException> {
            batch {
                source.set(1)
                error("boom")
            }
        }

        assertEquals(listOf(1), observed)
        owner.dispose()
    }

    @Test
    fun aDiamondInsideABatchStillSettlesBeforeTheEffectRuns() {
        val root = mutableStateOf(1)
        val left = memo { root() * 2 }
        val right = memo { root() * 3 }
        val sum = memo { left() + right() }
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(sum()) }
            owner
        }
        observed.clear()

        batch {
            root.set(2)
            root.set(3)
        }

        assertEquals(listOf(15), observed)
        owner.dispose()
    }

    @Test
    fun batchingDefersDispatchWithoutBypassingTheScheduler() {
        val queued = mutableListOf<() -> Unit>()
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner(scheduler = { task -> queued.add(task) })
        runWithOwner(root) { effect { observed.add(source()) } }
        queued.removeFirst().invoke()
        observed.clear()

        batch {
            source.set(1)
            source.set(2)
        }

        assertEquals(1, queued.size)
        assertEquals(emptyList<Int>(), observed)

        queued.removeFirst().invoke()
        assertEquals(listOf(2), observed)

        root.dispose()
    }

    @Test
    fun writesOutsideABatchStillFlushImmediately() {
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
}
