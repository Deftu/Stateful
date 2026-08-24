import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.State
import dev.deftu.stateful.collections.mapKeyed
import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every documented usage pattern that depends only on this library, run against the real API.
 *
 * A pattern nobody executes is an untested claim. Patterns needing a foreign framework cannot be
 * checked from here; each adapter module's own tests cover those.
 */
class UsageSamplesTest {
    private data class Item(val price: Int)

    private class Cart {
        private val items = mutableStateOf<List<Item>>(emptyList())

        val subtotal: State<Int> = memo { items().sumOf { it.price } }
        val itemCount: State<Int> = memo { items().size }
        val isEmpty: State<Boolean> = memo { items().isEmpty() }

        fun add(item: Item) {
            items.update { it + item }
        }
    }

    private data class Todo(val id: Int, val title: String, val done: Boolean = false)

    @Test
    fun theTrackedAndUntrackedRuleReadsAsDocumented() {
        val name = mutableStateOf("world")

        val greeting = memo { "hello, ${name()}" }
        val once = name.value

        assertEquals("hello, world", greeting.value)
        assertEquals("world", once)
    }

    @Test
    fun thePlainKotlinSampleRuns() {
        val printed = mutableListOf<String>()

        val root = createRoot { root ->
            val celsius = mutableStateOf(20.0)
            val fahrenheit = memo { celsius() * 9 / 5 + 32 }

            effect { printed.add("${celsius()} -> ${fahrenheit()}") }

            celsius.set(25.0)
            celsius.set(30.0)

            root
        }

        assertEquals(3, printed.size)
        root.dispose()
    }

    @Test
    fun aClassExposingDerivedStateNeedsNoOwner() {
        val cart = Cart()

        assertEquals(0, cart.subtotal.value)
        assertEquals(0, cart.itemCount.value)
        assertTrue(cart.isEmpty.value)

        cart.add(Item(price = 5))
        cart.add(Item(price = 3))

        assertEquals(8, cart.subtotal.value)
        assertEquals(2, cart.itemCount.value)
        assertTrue(!cart.isEmpty.value)
    }

    @Test
    fun theOneShotSampleNeedsNoLifetime() {
        val arguments = listOf("--verbose")
        val verbose = mutableStateOf(arguments.contains("--verbose"))
        val level = memo { if (verbose()) "DEBUG" else "INFO" }

        assertEquals("DEBUG", level.value)
    }

    @Test
    fun theTestingSampleUsesAnOwnerPerTest() {
        val root: Owner = createOwner()
        val cart = Cart()
        val observed = mutableListOf<Int>()

        runWithOwner(root) { effect { observed.add(cart.subtotal()) } }

        cart.add(Item(price = 5))
        cart.add(Item(price = 3))

        assertEquals(listOf(0, 5, 8), observed)
        root.dispose()
    }

    @Test
    fun theQueuedSchedulerSampleIsDeterministic() {
        val cart = Cart()
        val observed = mutableListOf<Int>()

        val scheduler = Scheduler.queued()
        val root = createOwner(scheduler)

        runWithOwner(root) { effect { observed.add(cart.subtotal()) } }
        scheduler.drain()

        cart.add(Item(price = 5))
        scheduler.drain()

        assertEquals(listOf(0, 5), observed)
        root.dispose()
    }

    @Test
    fun theCollectionSampleNotifiesPrecisely() {
        val todos = reactiveListOf(Todo(1, "write"), Todo(2, "test"))
        val sizes = mutableListOf<Int>()
        val first = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { sizes.add(todos.size) }
            effect { first.add(todos[0].title) }
            owner
        }

        todos[1] = todos.getUntracked(1).copy(done = true)

        assertEquals(listOf(2), sizes)
        assertEquals(listOf("write"), first)

        owner.dispose()
    }

    @Test
    fun theWholeListSampleTracksEveryElement() {
        val todos = reactiveListOf(Todo(1, "write"), Todo(2, "test"))
        val counts = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { counts.add(todos.count { it.done }) }
            owner
        }

        todos[0] = todos.getUntracked(0).copy(done = true)

        assertEquals(listOf(0, 1), counts)
        owner.dispose()
    }

    @Test
    fun theKeyedReconciliationSampleReusesRows() {
        val todos = reactiveListOf(Todo(1, "write"), Todo(2, "test"))
        var builds = 0

        val owner = createRoot { owner ->
            todos.mapKeyed(key = { todo -> todo.id }) { todo, index ->
                builds++
                memo { "${todo().title}@${index()}" }
            }
            owner
        }

        assertEquals(2, builds)

        todos.move(0, 1)

        assertEquals(2, builds)
        owner.dispose()
    }

    @Test
    fun aStateBoundToAHostLifetimeDiesWithIt() {
        val source: MutableState<Int> = mutableStateOf(0)
        val observed = mutableListOf<Int>()

        val root = createOwner()
        runWithOwner(root) { effect { observed.add(source()) } }

        source.set(1)
        root.dispose()
        source.set(2)

        assertEquals(listOf(0, 1), observed)
    }
}
