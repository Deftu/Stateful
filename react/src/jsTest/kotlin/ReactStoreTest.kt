import dev.deftu.stateful.Equality
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import dev.deftu.stateful.react.asExternalStore
import dev.deftu.stateful.react.statefulRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReactStoreTest {
    private data class Row(val label: String)

    @Test
    fun subscribingIsNotifiedWithoutAValue() {
        val source = mutableStateOf(1)
        val store = source.asExternalStore()
        var notifications = 0

        val unsubscribe = store.subscribe { notifications++ }
        source.set(2)
        source.set(3)

        assertEquals(2, notifications)
        unsubscribe()
    }

    @Test
    fun theSnapshotIsReadThroughGetSnapshot() {
        val source = mutableStateOf("a")
        val store = source.asExternalStore()

        assertEquals("a", store.getSnapshot())

        source.set("b")

        assertEquals("b", store.getSnapshot())
    }

    @Test
    fun unsubscribingStopsNotifications() {
        val source = mutableStateOf(1)
        val store = source.asExternalStore()
        var notifications = 0

        val unsubscribe = store.subscribe { notifications++ }
        source.set(2)
        unsubscribe()
        source.set(3)

        assertEquals(1, notifications)
    }

    @Test
    fun repeatedSnapshotsAreReferentiallyStable() {
        val source = mutableStateOf(Row("one"))
        val store = source.asExternalStore()

        val first = store.getSnapshot()
        val second = store.getSnapshot()

        assertSame(first, second)
    }

    @Test
    fun aMemoKeepsTheSnapshotStableUntilTheValueChanges() {
        val source = mutableStateOf(1)
        val derived = memo { Row("row ${source()}") }
        val store = derived.asExternalStore()

        val before = store.getSnapshot()
        assertSame(before, store.getSnapshot())

        source.set(1)

        assertSame(before, store.getSnapshot())
    }

    @Test
    fun aSnapshotChangesIdentityOnlyWhenTheValueDoes() {
        val source = mutableStateOf("a")
        val derived = memo { Row(source()) }
        val store = derived.asExternalStore()

        val before = store.getSnapshot()

        source.set("b")
        val after = store.getSnapshot()

        assertTrue(before !== after)
        assertEquals("b", after.label)
    }

    @Test
    fun aNeverEqualStateNotifiesOnEveryWriteWhichReactCannotSettle() {
        val source = mutableStateOf(1, Equality.never())
        val store = source.asExternalStore()
        var notifications = 0

        val unsubscribe = store.subscribe { notifications++ }
        source.set(1)
        source.set(1)

        assertEquals(2, notifications)
        unsubscribe()
    }

    @Test
    fun aRootDisposesWhatWasCreatedUnderIt() {
        val source = mutableStateOf(0)
        val observed = mutableListOf<Int>()
        val root = statefulRoot()

        runWithOwner(root) { effect { observed.add(source()) } }

        source.set(1)
        root.dispose()
        source.set(2)

        assertEquals(listOf(0, 1), observed)
        assertTrue(root.isDisposed)
    }

    @Test
    fun aDiscardedRootHoldsNothing() {
        val root = statefulRoot()

        assertTrue(root.isEmpty)

        root.dispose()

        assertTrue(root.isEmpty)
    }

    @Test
    fun theStoreShapeIsWhatTheHookExpects() {
        val source = mutableStateOf(1)
        val store: dynamic = source.asExternalStore()

        assertTrue(jsTypeOf(store.subscribe) == "function")
        assertTrue(jsTypeOf(store.getSnapshot) == "function")

        val unsubscribe = store.subscribe { }
        assertTrue(jsTypeOf(unsubscribe) == "function")
        unsubscribe()
    }
}
