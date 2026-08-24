import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.rawStateOf
import dev.deftu.stateful.snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SnapshotTest {
    private data class Profile(val name: String, val tags: List<String>)

    @Test
    fun aSnapshotOfAPlainValueIsThatValue() {
        val source = mutableStateOf(42)
        assertEquals(42, snapshot(source))
    }

    @Test
    fun aSnapshotDoesNotChangeWhenTheStateDoes() {
        val source = mutableStateOf(listOf("a", "b"))
        val taken = snapshot(source)

        source.set(listOf("c"))

        assertEquals(listOf("a", "b"), taken)
    }

    @Test
    fun aSnapshotCopiesCollectionsRatherThanSharingThem() {
        val backing = mutableListOf("a", "b")
        val source = mutableStateOf<List<String>>(backing)
        val taken = snapshot(source)

        backing.add("c")

        assertEquals(listOf("a", "b"), taken)
        assertNotSame(backing, taken)
    }

    @Test
    fun aSnapshotUnwrapsNestedStatesAndSoWidensTheStaticType() {
        val inner = mutableStateOf("inner")
        val outer = mutableStateOf<List<State<String>>>(listOf(inner))

        val taken = snapshot<Any?>(outer)

        assertEquals(listOf("inner"), taken)

        inner.set("changed")

        assertEquals(listOf("inner"), taken)
    }

    @Test
    fun aSnapshotCopiesNestedCollections() {
        val source = mutableStateOf(mapOf("tags" to listOf("x", "y")))
        val taken = snapshot(source)

        assertEquals(mapOf("tags" to listOf("x", "y")), taken)
    }

    @Test
    fun aSnapshotOfASetPreservesOrderAndContents() {
        val source = mutableStateOf(linkedSetOf(3, 1, 2))
        val taken: Set<Int> = snapshot(source)

        assertEquals(listOf(3, 1, 2), taken.toList())
    }

    @Test
    fun aSnapshotSharesObjectsItCannotCopy() {
        val profile = Profile("ada", listOf("admin"))
        val source = mutableStateOf(profile)

        assertSame(profile, snapshot(source))
    }

    @Test
    fun aSnapshotReadIsUntracked() {
        val source = mutableStateOf(1)
        var runs = 0

        val derived = memo {
            runs++
            snapshot(source)
        }

        assertEquals(1, derived.value)
        assertEquals(1, runs)

        source.set(2)

        assertEquals(1, derived.value)
        assertEquals(1, runs)
    }

    @Test
    fun aSnapshotOfAMemoResolvesItFirst() {
        val source = mutableStateOf(2)
        val doubled = memo { source() * 2 }

        assertEquals(4, snapshot(doubled))

        source.set(5)

        assertEquals(10, snapshot(doubled))
    }

    @Test
    fun aRawStateFiresOnReplacementWithAnEqualValue() {
        val first = listOf("a")
        val second = listOf("a")
        val source = rawStateOf(first)
        val observed = mutableListOf<List<String>>()
        source.subscribe { value -> observed.add(value) }

        source.set(second)

        assertEquals(listOf(second), observed)
        assertFalse(first === second)
    }

    @Test
    fun aRawStateIgnoresAWriteOfTheSameInstance() {
        val value = listOf("a")
        val source = rawStateOf(value)
        val observed = mutableListOf<List<String>>()
        source.subscribe { seen -> observed.add(seen) }

        source.set(value)

        assertTrue(observed.isEmpty())
    }

    @Test
    fun anOrdinaryStateIgnoresAWriteOfAnEqualValue() {
        val source = mutableStateOf(listOf("a"))
        val observed = mutableListOf<List<String>>()
        source.subscribe { value -> observed.add(value) }

        source.set(listOf("a"))

        assertTrue(observed.isEmpty())
    }
}
