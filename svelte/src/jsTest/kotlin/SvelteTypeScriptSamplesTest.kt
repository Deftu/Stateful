import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.svelte.Writable
import dev.deftu.stateful.svelte.asSvelteStore
import kotlin.test.Test
import kotlin.test.assertEquals

private val count = mutableStateOf(0)

/**
 * The shape the documented TypeScript sample imports.
 *
 * Only `@JsExport`ed declarations reach the emitted `.d.ts`, and every type in an exported
 * signature must itself be exportable — so the store crosses and the `MutableState` behind it
 * stays on the Kotlin side.
 */
@JsExport
val countStore: Writable<Int> = count.asSvelteStore()

class SvelteTypeScriptSamplesTest {
    @Test
    fun theExportedStoreWritesThroughToItsKotlinSource() {
        val rendered = mutableListOf<Int>()
        val unsubscribe = countStore.subscribe { value -> rendered.add(value) }

        countStore.set(1)
        countStore.update { n -> n + 1 }

        assertEquals(listOf(0, 1, 2), rendered)
        assertEquals(2, count.value)

        unsubscribe()
        countStore.set(0)

        assertEquals(listOf(0, 1, 2), rendered)
    }
}
