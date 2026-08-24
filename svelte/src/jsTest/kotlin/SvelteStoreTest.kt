import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.svelte.asSvelteStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SvelteStoreTest {
    @Test
    fun subscribingCallsTheSubscriberImmediately() {
        val source = mutableStateOf("initial")
        val store = (source as dev.deftu.stateful.State<String>).asSvelteStore()
        val seen = mutableListOf<String>()

        val unsubscribe = store.subscribe { value -> seen.add(value) }

        assertEquals(listOf("initial"), seen)
        unsubscribe()
    }

    @Test
    fun aSubscriberReceivesEveryChange() {
        val source = mutableStateOf(1)
        val store = (source as dev.deftu.stateful.State<Int>).asSvelteStore()
        val seen = mutableListOf<Int>()

        val unsubscribe = store.subscribe { value -> seen.add(value) }
        source.set(2)
        source.set(3)

        assertEquals(listOf(1, 2, 3), seen)
        unsubscribe()
    }

    @Test
    fun unsubscribingStopsDelivery() {
        val source = mutableStateOf(1)
        val store = (source as dev.deftu.stateful.State<Int>).asSvelteStore()
        val seen = mutableListOf<Int>()

        val unsubscribe = store.subscribe { value -> seen.add(value) }
        source.set(2)
        unsubscribe()
        source.set(3)

        assertEquals(listOf(1, 2), seen)
    }

    @Test
    fun severalSubscribersEachGetTheirOwnImmediateCall() {
        val source = mutableStateOf("a")
        val store = (source as dev.deftu.stateful.State<String>).asSvelteStore()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()

        val stopFirst = store.subscribe { value -> first.add(value) }
        source.set("b")
        val stopSecond = store.subscribe { value -> second.add(value) }
        source.set("c")

        assertEquals(listOf("a", "b", "c"), first)
        assertEquals(listOf("b", "c"), second)

        stopFirst()
        stopSecond()
    }

    @Test
    fun aDerivedStateBecomesAStore() {
        val source = mutableStateOf(2)
        val doubled = memo { source() * 2 }
        val store = doubled.asSvelteStore()
        val seen = mutableListOf<Int>()

        val unsubscribe = store.subscribe { value -> seen.add(value) }
        source.set(5)

        assertEquals(listOf(4, 10), seen)
        unsubscribe()
    }

    @Test
    fun aWritableStoreWritesThrough() {
        val source = mutableStateOf(1)
        val store = source.asSvelteStore()

        store.set(7)

        assertEquals(7, source.value)
    }

    @Test
    fun aWritableStoreUpdatesThrough() {
        val source = mutableStateOf(10)
        val store = source.asSvelteStore()

        store.update { current -> current + 5 }

        assertEquals(15, source.value)
    }

    @Test
    fun aWriteThroughTheStoreReachesItsOwnSubscribers() {
        val source = mutableStateOf(0)
        val store = source.asSvelteStore()
        val seen = mutableListOf<Int>()

        val unsubscribe = store.subscribe { value -> seen.add(value) }
        store.set(1)
        store.update { it + 1 }

        assertEquals(listOf(0, 1, 2), seen)
        unsubscribe()
    }

    @Test
    fun theStoreShapeMatchesWhatSvelteDuckTypesFor() {
        val source = mutableStateOf(1)
        val store: dynamic = source.asSvelteStore()

        assertTrue(jsTypeOf(store.subscribe) == "function")
        assertTrue(jsTypeOf(store.set) == "function")
        assertTrue(jsTypeOf(store.update) == "function")

        val unsubscribe = store.subscribe { _: Int -> }
        assertTrue(jsTypeOf(unsubscribe) == "function")
        unsubscribe()
    }
}
