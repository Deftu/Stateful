import dev.deftu.stateful.collections.ListChange
import dev.deftu.stateful.collections.mapKeyed
import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactiveListTest {
    private data class Row(val id: Int, val label: String)

    @Test
    fun replacingOneElementDoesNotWakeAConsumerOfAnother() {
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
    fun replacingAnElementDoesNotWakeASizeConsumer() {
        val list = reactiveListOf("a", "b")
        val sizes = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { sizes.add(list.size) }
            owner
        }

        list[0] = "A"

        assertEquals(listOf(2), sizes)
        owner.dispose()
    }

    @Test
    fun appendingWakesASizeConsumer() {
        val list = reactiveListOf("a")
        val sizes = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { sizes.add(list.size) }
            owner
        }

        list.add("b")

        assertEquals(listOf(1, 2), sizes)
        owner.dispose()
    }

    @Test
    fun insertingAtTheFrontShiftsValuesThroughSlots() {
        val list = reactiveListOf("a", "b")
        val first = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { first.add(list[0]) }
            owner
        }

        list.add(0, "z")

        assertEquals(listOf("a", "z"), first)
        owner.dispose()
    }

    @Test
    fun removalShiftsValuesThroughSlots() {
        val list = reactiveListOf("a", "b", "c")
        val first = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { first.add(list[0]) }
            owner
        }

        list.removeAt(0)

        assertEquals(listOf("a", "b"), first)
        owner.dispose()
    }

    @Test
    fun aReplacementEmitsASetChangeCarryingThePreviousValue() {
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
    fun addAllEmitsOneRangedInsert() {
        val list = reactiveListOf("a")
        val seen = mutableListOf<ListChange<String>>()

        val owner = createRoot { owner ->
            effect { seen.addAll(list.changes()) }
            owner
        }
        seen.clear()

        list.addAll(listOf("b", "c"))

        assertEquals(listOf<ListChange<String>>(ListChange.Insert(1, listOf("b", "c"))), seen)
        owner.dispose()
    }

    @Test
    fun moveEmitsAMoveRatherThanARemovalAndAnInsert() {
        val list = reactiveListOf("a", "b", "c")
        val seen = mutableListOf<ListChange<String>>()

        val owner = createRoot { owner ->
            effect { seen.addAll(list.changes()) }
            owner
        }
        seen.clear()

        list.move(0, 2)

        assertEquals(listOf<ListChange<String>>(ListChange.Move(0, 2, "a")), seen)
        assertEquals(listOf("b", "c", "a"), list.toList())
        owner.dispose()
    }

    @Test
    fun clearEmitsTheRemovedContents() {
        val list = reactiveListOf("a", "b")
        val seen = mutableListOf<ListChange<String>>()

        val owner = createRoot { owner ->
            effect { seen.addAll(list.changes()) }
            owner
        }
        seen.clear()

        list.clear()

        assertEquals(listOf<ListChange<String>>(ListChange.Clear(listOf("a", "b"))), seen)
        assertEquals(0, list.untrackedSize)
        owner.dispose()
    }

    @Test
    fun anUntrackedReadRegistersNothing() {
        val list = reactiveListOf("a", "b")
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
    fun keyedReconciliationBuildsOnceForEachKey() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
        var builds = 0

        val owner = createRoot { owner ->
            list.mapKeyed(key = { it.id }) { element, _ ->
                builds++
                element
            }
            owner
        }

        assertEquals(2, builds)

        list[0] = Row(1, "ONE")

        assertEquals(2, builds)
        owner.dispose()
    }

    @Test
    fun keyedReconciliationReusesResultsAcrossAMove() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"), Row(3, "three"))
        var builds = 0

        lateinit var rows: dev.deftu.stateful.State<List<String>>
        val owner = createRoot { owner ->
            rows = list.mapKeyed(key = { it.id }) { element, _ ->
                builds++
                element.value.label
            }
            owner
        }

        assertEquals(3, builds)

        list.move(0, 2)

        assertEquals(3, builds)
        assertEquals(listOf("two", "three", "one"), rows.value)
        owner.dispose()
    }

    @Test
    fun keyedReconciliationTracksTheIndexOfAMovedElement() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
        val positions = mutableListOf<String>()

        val owner = createRoot { owner ->
            list.mapKeyed(key = { it.id }) { element, index ->
                effect { positions.add("${element().label}@${index()}") }
            }
            owner
        }
        positions.clear()

        list.move(0, 1)

        assertTrue(positions.contains("one@1"), "expected one@1 in $positions")
        assertTrue(positions.contains("two@0"), "expected two@0 in $positions")
        owner.dispose()
    }

    @Test
    fun keyedReconciliationDisposesTheOwnerOfADepartedElement() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
        val cleaned = mutableListOf<Int>()

        val owner = createRoot { owner ->
            list.mapKeyed(key = { it.id }) { element, _ ->
                val id = element.value.id
                dev.deftu.stateful.dsl.onCleanup { cleaned.add(id) }
            }
            owner
        }

        list.removeAt(0)

        assertEquals(listOf(1), cleaned)

        owner.dispose()

        assertEquals(listOf(1, 2), cleaned)
    }

    @Test
    fun keyedReconciliationBuildsForNewKeysOnly() {
        val list = reactiveListOf(Row(1, "one"))
        val built = mutableListOf<Int>()

        val owner = createRoot { owner ->
            list.mapKeyed(key = { it.id }) { element, _ ->
                built.add(element.value.id)
            }
            owner
        }

        list.add(Row(2, "two"))
        list.add(Row(3, "three"))

        assertEquals(listOf(1, 2, 3), built)
        owner.dispose()
    }

    @Test
    fun aWholeListReadTracksEveryElement() {
        val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
        var runs = 0
        val labels = memo {
            runs++
            list.joinToString { it.label }
        }

        assertEquals("one, two", labels.value)
        assertEquals(1, runs)

        list[0] = Row(1, "ONE")

        assertEquals("ONE, two", labels.value)
        assertEquals(2, runs)
    }

    @Test
    fun aFilteringReadTracksElementReplacement() {
        val list = reactiveListOf(Row(1, "a"), Row(2, "b"))
        val matching = memo { list.count { it.label == "a" } }

        assertEquals(1, matching.value)

        list[1] = Row(2, "a")

        assertEquals(2, matching.value)
    }
}
