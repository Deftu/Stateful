import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.react.asExternalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ReactReadmeSamplesTest {
    private data class Item(val id: Int, val label: String)

    private data class Row(val label: String)

    private fun toRow(item: Item) = Row(item.label)

    @Test
    fun aMemoKeepsTheSnapshotStableBetweenChanges() {
        val items = reactiveListOf(Item(1, "one"), Item(2, "two"))
        val rows = memo { items.map(::toRow) }
        val store = rows.asExternalStore()

        val first = store.getSnapshot()
        assertSame(first, store.getSnapshot())

        items[0] = Item(1, "ONE")

        val second = store.getSnapshot()
        assertEquals(listOf(Row("ONE"), Row("two")), second)
    }
}
