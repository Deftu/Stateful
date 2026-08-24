import dev.deftu.stateful.collections.reactiveMapOf
import dev.deftu.stateful.collections.reactiveSetOf
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReactiveMapSetTest {
    @Test
    fun replacingOneEntryDoesNotWakeAConsumerOfAnother() {
        val map = reactiveMapOf("a" to 1, "b" to 2)
        val first = mutableListOf<Int?>()
        val second = mutableListOf<Int?>()

        val owner = createRoot { owner ->
            effect { first.add(map["a"]) }
            effect { second.add(map["b"]) }
            owner
        }

        map["b"] = 20

        assertEquals(listOf<Int?>(1), first)
        assertEquals(listOf<Int?>(2, 20), second)

        owner.dispose()
    }

    @Test
    fun readingAnAbsentKeyWakesOnItsLaterInsert() {
        val map = reactiveMapOf<String, Int>()
        val observed = mutableListOf<Int?>()

        val owner = createRoot { owner ->
            effect { observed.add(map["pending"]) }
            owner
        }

        map["pending"] = 7

        assertEquals(listOf(null, 7), observed)
        owner.dispose()
    }

    @Test
    fun replacingAnEntryDoesNotWakeASizeConsumer() {
        val map = reactiveMapOf("a" to 1)
        val sizes = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { sizes.add(map.size) }
            owner
        }

        map["a"] = 2

        assertEquals(listOf(1), sizes)
        owner.dispose()
    }

    @Test
    fun insertingANewKeyWakesASizeConsumer() {
        val map = reactiveMapOf("a" to 1)
        val sizes = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { sizes.add(map.size) }
            owner
        }

        map["b"] = 2

        assertEquals(listOf(1, 2), sizes)
        owner.dispose()
    }

    @Test
    fun removingAKeyWakesItsConsumerWithNull() {
        val map = reactiveMapOf("a" to 1)
        val observed = mutableListOf<Int?>()

        val owner = createRoot { owner ->
            effect { observed.add(map["a"]) }
            owner
        }

        map.remove("a")

        assertEquals(listOf(1, null), observed)
        owner.dispose()
    }

    @Test
    fun clearingWakesEveryKeyConsumerOnce() {
        val map = reactiveMapOf("a" to 1, "b" to 2)
        val observed = mutableListOf<String>()

        val owner = createRoot { owner ->
            effect { observed.add("a=${map["a"]}") }
            effect { observed.add("b=${map["b"]}") }
            owner
        }
        observed.clear()

        map.clear()

        assertEquals(listOf("a=null", "b=null"), observed)
        owner.dispose()
    }

    @Test
    fun anUntrackedMapReadRegistersNothing() {
        val map = reactiveMapOf("a" to 1)
        var runs = 0

        val derived = memo {
            runs++
            map.getUntracked("a")
        }

        assertEquals(1, derived.value)

        map["a"] = 2

        assertEquals(1, derived.value)
        assertEquals(1, runs)
    }

    @Test
    fun membershipOfOneElementIsIndependentOfAnother() {
        val set = reactiveSetOf("a")
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()

        val owner = createRoot { owner ->
            effect { first.add("a" in set) }
            effect { second.add("b" in set) }
            owner
        }

        set.add("b")

        assertEquals(listOf(true), first)
        assertEquals(listOf(false, true), second)

        owner.dispose()
    }

    @Test
    fun askingAboutAnAbsentElementWakesOnItsLaterAdd() {
        val set = reactiveSetOf<String>()
        val observed = mutableListOf<Boolean>()

        val owner = createRoot { owner ->
            effect { observed.add("later" in set) }
            owner
        }

        set.add("later")

        assertEquals(listOf(false, true), observed)
        owner.dispose()
    }

    @Test
    fun removingAnElementWakesItsConsumer() {
        val set = reactiveSetOf("a")
        val observed = mutableListOf<Boolean>()

        val owner = createRoot { owner ->
            effect { observed.add("a" in set) }
            owner
        }

        set.remove("a")

        assertEquals(listOf(true, false), observed)
        owner.dispose()
    }

    @Test
    fun addingAnElementAlreadyPresentChangesNothing() {
        val set = reactiveSetOf("a")
        val observed = mutableListOf<Boolean>()

        val owner = createRoot { owner ->
            effect { observed.add("a" in set) }
            owner
        }
        observed.clear()

        assertFalse(set.add("a"))

        assertTrue(observed.isEmpty())
        owner.dispose()
    }

    @Test
    fun anUntrackedMembershipTestRegistersNothing() {
        val set = reactiveSetOf("a")
        var runs = 0

        val derived = memo {
            runs++
            set.containsUntracked("a")
        }

        assertTrue(derived.value)

        set.remove("a")

        assertTrue(derived.value)
        assertEquals(1, runs)
    }
}
