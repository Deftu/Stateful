import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.coroutines.asFlow
import dev.deftu.stateful.coroutines.asScheduler
import dev.deftu.stateful.coroutines.asState
import dev.deftu.stateful.coroutines.asStateFlow
import dev.deftu.stateful.coroutines.disposeWith
import dev.deftu.stateful.coroutines.statefulOwner
import dev.deftu.stateful.coroutines.toState
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowInteropTest {
    @Test
    fun aFlowEmitsTheCurrentValueFirst() = runTest {
        val source = mutableStateOf(1)
        val values = mutableListOf<Int>()

        val job = launch { source.asFlow().take(1).collect { value -> values.add(value) } }
        job.join()

        assertEquals(listOf(1), values)
    }

    @Test
    fun aFlowEmitsSubsequentChanges() = runTest {
        val source = mutableStateOf(1)
        val values = mutableListOf<Int>()

        val job = launch { source.asFlow().take(3).collect { value -> values.add(value) } }

        yield()
        source.set(2)
        yield()
        source.set(3)
        job.join()

        assertEquals(listOf(1, 2, 3), values)
    }

    @Test
    fun cancellingCollectionStopsObserving() = runTest {
        val source = mutableStateOf(1)
        val values = mutableListOf<Int>()

        val job = launch { source.asFlow().collect { value -> values.add(value) } }

        yield()
        source.set(2)
        yield()
        job.cancel()
        job.join()

        source.set(3)
        yield()

        assertEquals(listOf(1, 2), values)
    }

    @Test
    fun aFlowCollectsIntoAState() = runTest {
        val flow = MutableSharedFlow<Int>()
        val state = flow.toState(backgroundScope, initial = 0)

        assertEquals(0, state.value)

        yield()
        flow.emit(1)
        yield()

        assertEquals(1, state.value)
    }

    @Test
    fun aStateFlowSeedsTheStateWithItsCurrentValue() = runTest {
        val flow = MutableStateFlow(7)
        val state = flow.asState(backgroundScope)

        assertEquals(7, state.value)

        flow.value = 8
        yield()

        assertEquals(8, state.value)
    }

    @Test
    fun aStateBecomesAStateFlow() = runTest {
        val source = mutableStateOf("a")
        val flow = source.asStateFlow(backgroundScope)

        assertEquals("a", flow.value)

        source.set("b")
        yield()

        assertEquals("b", flow.value)
    }

    @Test
    fun aDerivedStateSurvivesTheRoundTrip() = runTest {
        val source = mutableStateOf(2)
        val doubled = memo { source() * 2 }
        val values = mutableListOf<Int>()

        val job = launch { doubled.asFlow().take(2).collect { value -> values.add(value) } }

        yield()
        source.set(5)
        job.join()

        assertEquals(listOf(4, 10), values)
    }

    @Test
    fun anOwnerBoundToAScopeDiesWithIt() = runTest {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val scope = CoroutineScope(coroutineContext + Job())
        val owner = scope.statefulOwner(Scheduler.Immediate)
        runWithOwner(owner) { effect { observed.add(source()) } }

        source.set(1)
        assertEquals(listOf(0, 1), observed)

        scope.cancel()
        yield()

        assertTrue(owner.isDisposed)

        source.set(2)
        assertEquals(listOf(0, 1), observed)
    }

    @Test
    fun anOwnerCanBeBoundToAJobDirectly() = runTest {
        val job = Job()
        val owner = createOwner()
        owner.disposeWith(job)

        assertFalse(owner.isDisposed)

        job.complete()
        yield()

        assertTrue(owner.isDisposed)
    }

    @Test
    fun aSchedulerRunsEffectBodiesInItsScope() = runTest {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val owner = createOwner(backgroundScope.asScheduler())
        runWithOwner(owner) { effect { observed.add(source()) } }

        yield()
        assertEquals(listOf(0), observed)

        source.set(1)
        yield()

        assertEquals(listOf(0, 1), observed)
        owner.dispose()
    }
}
