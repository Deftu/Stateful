import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.react.ExternalStore
import dev.deftu.stateful.react.asExternalStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private val counter = mutableStateOf(0)

/**
 * The shape the documented TypeScript sample imports.
 *
 * Only `@JsExport`ed declarations reach the emitted `.d.ts`, and every type in an exported
 * signature must itself be exportable — so the store crosses and the `State` behind it stays on
 * the Kotlin side. `increment` crosses too, because TypeScript has no other way to write.
 */
@JsExport
val counterStore: ExternalStore<Int> = memo { counter() }.asExternalStore()

@JsExport
fun increment() {
    counter.update { it + 1 }
}

class ReactTypeScriptSamplesTest {
    @Test
    fun theExportedStoreNotifiesWithoutAValueAndReadsThroughGetSnapshot() {
        var notifications = 0
        val unsubscribe = counterStore.subscribe { notifications++ }

        assertEquals(0, counterStore.getSnapshot())

        increment()

        assertEquals(1, notifications)
        assertEquals(1, counterStore.getSnapshot())

        unsubscribe()
        increment()

        assertEquals(1, notifications)
    }

    @Test
    fun theSnapshotIsStableBetweenNotificationsSoReactCanConverge() {
        val rows = memo { listOf("row ${counter()}") }
        val store = rows.asExternalStore()

        assertSame(store.getSnapshot(), store.getSnapshot())
    }
}
