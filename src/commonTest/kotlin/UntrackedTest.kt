import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.untracked
import kotlin.test.Test
import kotlin.test.assertEquals

class UntrackedTest {
    @Test
    fun anUntrackedReadInsideAMemoDoesNotCreateADependency() {
        val tracked = mutableStateOf(1)
        val ignored = mutableStateOf(100)
        var runs = 0

        val combined = memo {
            runs++
            tracked() + untracked { ignored() }
        }

        assertEquals(101, combined.value)
        assertEquals(1, runs)

        ignored.set(200)
        assertEquals(101, combined.value)
        assertEquals(1, runs)

        tracked.set(2)
        assertEquals(202, combined.value)
        assertEquals(2, runs)
    }

    @Test
    fun anUntrackedReadInsideAnEffectDoesNotWakeIt() {
        val tracked = mutableStateOf(1)
        val ignored = mutableStateOf(100)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            effect { observed.add(tracked() + untracked { ignored() }) }
            owner
        }

        ignored.set(200)
        assertEquals(listOf(101), observed)

        tracked.set(2)
        assertEquals(listOf(101, 202), observed)

        owner.dispose()
    }

    @Test
    fun thePropertyReadIsUntrackedWithoutAnUntrackedBlock() {
        val tracked = mutableStateOf(1)
        val ignored = mutableStateOf(100)
        var runs = 0

        val combined = memo {
            runs++
            tracked() + ignored.value
        }

        assertEquals(101, combined.value)

        ignored.set(200)
        assertEquals(101, combined.value)
        assertEquals(1, runs)
    }

    @Test
    fun trackingResumesAfterAnUntrackedBlock() {
        val before = mutableStateOf(1)
        val inside = mutableStateOf(10)
        val after = mutableStateOf(100)
        var runs = 0

        val combined = memo {
            runs++
            before() + untracked { inside() } + after()
        }

        assertEquals(111, combined.value)

        inside.set(20)
        assertEquals(111, combined.value)
        assertEquals(1, runs)

        after.set(200)
        assertEquals(221, combined.value)
        assertEquals(2, runs)
    }

    @Test
    fun nestedUntrackedBlocksRestoreTheOuterState() {
        val outer = mutableStateOf(1)
        val inner = mutableStateOf(10)
        var runs = 0

        val combined = memo {
            runs++
            untracked { untracked { inner() } } + outer()
        }

        assertEquals(11, combined.value)

        inner.set(20)
        assertEquals(11, combined.value)
        assertEquals(1, runs)

        outer.set(2)
        assertEquals(22, combined.value)
        assertEquals(2, runs)
    }

    @Test
    fun untrackedReturnsTheValueOfItsBlock() {
        val source = mutableStateOf(7)
        assertEquals(14, untracked { source() * 2 })
    }

    @Test
    fun aThrowingUntrackedBlockRestoresTracking() {
        val tracked = mutableStateOf(1)
        var runs = 0

        val combined = memo {
            runs++
            try {
                untracked { error("boom") }
            } catch (_: IllegalStateException) {
                Unit
            }
            tracked()
        }

        assertEquals(1, combined.value)

        tracked.set(2)
        assertEquals(2, combined.value)
        assertEquals(2, runs)
    }
}
