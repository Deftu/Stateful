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
 * built for it, with its index state updated; an element replaced in place keeps its result and
 * sees the new value through its element state. Only a genuinely new key builds anything, and only
 * a departed key tears anything down.
 *
 * This is what makes list-shaped UI viable. Deriving with `memo { list.map { … } }` instead is
 * correct but rebuilds every result on every change, which for a list of views means discarding and
 * recreating the whole list to move one row.
 *
 * Each result is built under its own child owner, so anything [transform] creates — nested effects,
 * cleanups — is disposed when that element leaves the list. The whole mapping is disposed with the
 * enclosing owner.
 *
 * ### Cost
 *
 * Replacing an element is **O(1)**: each entry watches its own position, so an edit wakes that
 * entry alone and reconciliation does not run at all. A structural change — insert, remove, move,
 * clear — is O(n), because every position after the edit point now holds a different element and
 * the key-to-result mapping has to be rebuilt.
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
        // Only the structure is tracked here. Element values are read untracked and delivered by
        // each entry's own effect below, so replacing one element does not drag the whole list
        // through reconciliation.
        val count = size

        val reconciled = LinkedHashMap<Any?, KeyedEntry<E, R>>(count)
        val output = ArrayList<R>(count)

        for (index in 0 until count) {
            val element = getUntracked(index)
            val identity = key(element)
            val existing = entries.remove(identity)

            val entry = if (existing != null) {
                if (existing.index.value != index) existing.index.set(index)
                existing.element.set(element)
                existing
            } else {
                val elementState = mutableStateOf(element)
                val indexState = mutableStateOf(index)
                val entryOwner = owner?.child()

                val result = if (entryOwner != null) {
                    runWithOwner(entryOwner) {
                        // Bound to the entry rather than to this reconciliation, so it survives
                        // re-runs and is what makes an in-place edit cost O(1). It watches its own
                        // slot and its own index, not the structure, so a move wakes only the
                        // positions that actually shifted.
                        //
                        // The key check is what makes that safe: an entry effect can run before the
                        // reconciler has corrected its index, and would otherwise adopt whichever
                        // element now sits at the stale position.
                        effect {
                            val position = indexState()
                            if (position < untrackedSize) {
                                val candidate = trackedSlot(position)
                                if (key(candidate) == identity) elementState.set(candidate)
                            }
                        }

                        transform(elementState, indexState)
                    }
                } else {
                    transform(elementState, indexState)
                }

                KeyedEntry(elementState, indexState, entryOwner, result)
            }

            reconciled[identity] = entry
            output.add(entry.result)
        }

        for (departed in entries.values) departed.owner?.dispose()
        entries.clear()
        entries.putAll(reconciled)

        results.set(output)
    }

    return results
}
