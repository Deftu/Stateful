package dev.deftu.stateful.collections

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.State
import dev.deftu.stateful.internal.tracking
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.runWithOwner

private class KeyedEntry<E, R>(
    val element: MutableState<E>,
    val index: MutableState<Int>,
    val owner: Owner?,
    val result: R,
)

/**
 * Derives one result per element, keyed by identity rather than by position.
 *
 * [transform] runs **once per key**, not once per change. An element that moves keeps the result
 * built for it, with its index state updated; an element that is replaced in place keeps its result
 * and sees the new value through its element state. Only a genuinely new key builds anything, and
 * only a departed key tears anything down.
 *
 * This is what makes list-shaped UI viable. Deriving with `memo { list.map { … } }` instead is
 * correct but rebuilds every result on every change, which for a list of views means discarding and
 * recreating the whole list to move one row.
 *
 * Each result is built under its own child owner, so anything [transform] creates — nested effects,
 * cleanups — is disposed when that element leaves the list. The whole mapping is disposed with the
 * enclosing owner.
 *
 * [transform] runs with the runtime lock released, so it may build views, touch other locks, and
 * create effects of its own.
 */
public fun <E, R> ReactiveList<E>.mapKeyed(
    key: (E) -> Any?,
    transform: (element: State<E>, index: State<Int>) -> R,
): State<List<R>> {
    val results = mutableStateOf<List<R>>(emptyList())
    val entries = LinkedHashMap<Any?, KeyedEntry<E, R>>()
    val owner = tracking.owner

    onCleanup {
        for (entry in entries.values) entry.owner?.dispose()
        entries.clear()
    }

    effect {
        val elements = toList()
        val reconciled = LinkedHashMap<Any?, KeyedEntry<E, R>>(elements.size)

        elements.forEachIndexed { index, element ->
            val identity = key(element)
            val existing = entries.remove(identity)

            val entry = if (existing != null) {
                existing.element.set(element)
                existing.index.set(index)
                existing
            } else {
                val elementState = mutableStateOf(element)
                val indexState = mutableStateOf(index)
                val entryOwner = owner?.child()
                val result = if (entryOwner != null) {
                    runWithOwner(entryOwner) { transform(elementState, indexState) }
                } else {
                    transform(elementState, indexState)
                }

                KeyedEntry(elementState, indexState, entryOwner, result)
            }

            reconciled[identity] = entry
        }

        for (departed in entries.values) departed.owner?.dispose()
        entries.clear()
        entries.putAll(reconciled)

        results.set(reconciled.values.map { it.result })
    }

    return results
}
