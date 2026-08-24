import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.svelte.asSvelteStore
import kotlin.test.Test
import kotlin.test.assertEquals

class SvelteReadmeSamplesTest {
    @Test
    fun theCounterStoreSampleWorks() {
        val count = mutableStateOf(0)
        val countStore = count.asSvelteStore()
        val rendered = mutableListOf<Int>()

        val unsubscribe = countStore.subscribe { value -> rendered.add(value) }
        countStore.update { n -> n + 1 }

        assertEquals(listOf(0, 1), rendered)
        assertEquals(1, count.value)

        unsubscribe()
    }
}
