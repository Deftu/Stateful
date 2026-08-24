import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.runWithOwner
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadmeSamplesTest {
    private class Display {
        var text: String = ""
    }

    private class View {
        var title: String = ""
        var detached: Boolean = false

        fun detach() {
            detached = true
        }
    }

    @Test
    fun theDiamondPrintsOnceWithTheSettledValue() {
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
    fun trackedAndUntrackedReadsDifferAsDocumented() {
        val name = mutableStateOf("world")

        val greeting = memo { "hello, ${name()}" }
        val once = name.value

        assertEquals("hello, world", greeting.value)
        assertEquals("world", once)

        name.set("there")

        assertEquals("hello, there", greeting.value)
        assertEquals("world", once)
    }

    @Test
    fun theTemperatureTourRuns() {
        val display = Display()
        val celsius = mutableStateOf(20.0)
        val fahrenheit = memo { celsius() * 9 / 5 + 32 }

        val owner = createRoot { owner ->
            effect { display.text = "${fahrenheit()}°F" }
            celsius.set(25.0)
            owner
        }

        assertEquals(77.0, fahrenheit.value)
        assertEquals("${77.0}°F", display.text)
        owner.dispose()
    }

    @Test
    fun theOwnershipTourRuns() {
        val view = View()
        val title = mutableStateOf("first")

        val owner = createOwner()
        runWithOwner(owner) {
            effect { view.title = title() }
            onCleanup { view.detach() }
        }

        title.set("second")
        assertEquals("second", view.title)

        owner.dispose()

        assertEquals(true, view.detached)
    }

    @Test
    fun theSchedulerTourRuns() {
        val frame = mutableStateOf(0)
        val rendered = mutableListOf<Int>()

        val scheduler = Scheduler.queued()
        val root = createOwner(scheduler)

        runWithOwner(root) { effect { rendered.add(frame()) } }
        scheduler.drain()

        assertEquals(listOf(0), rendered)
        root.dispose()
    }

    @Test
    fun theBatchTourRunsTheEffectOnce() {
        val first = mutableStateOf(1)
        val second = mutableStateOf(2)
        val runs = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { runs.add(first() + second()) }
            owner
        }
        runs.clear()

        batch {
            first.set(10)
            second.set(20)
        }

        assertEquals(listOf(30), runs)
        owner.dispose()
    }

    @Test
    fun theCollectionTourWakesOnlyTheAffectedConsumer() {
        val todos = reactiveListOf("write", "test")
        val zero = mutableListOf<String>()
        val one = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { zero.add(todos[0]) }
            effect { one.add(todos[1]) }
            owner
        }

        todos[1] = "ship"

        assertEquals(listOf("write"), zero)
        assertEquals(listOf("test", "ship"), one)
        owner.dispose()
    }
}
