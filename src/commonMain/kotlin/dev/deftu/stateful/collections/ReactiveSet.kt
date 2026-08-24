package dev.deftu.stateful.collections

import dev.deftu.stateful.Equality
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.mutableStateOf

/**
 * A set whose membership tests are tracked per element, so a consumer asking about one element is
 * not woken by another being added or removed.
 *
 * Every element asked about has a slot holding whether it is present, created on first tracked
 * read. Asking about an element that is **absent** still registers, so a later add wakes the
 * reader — which is the whole point for the usual shape of "is this selected".
 *
 * Reading [size] or iterating registers on the structure signal instead.
 *
 * Not thread-safe for concurrent structural edits. Reads are.
 */
public class ReactiveSet<E> internal constructor(initial: Set<E>) : AbstractMutableSet<E>() {
    private val backing = LinkedHashSet(initial)
    private val slots = HashMap<E, MutableState<Boolean>>()
    private val structure = mutableStateOf(0, Equality.never())

    override val size: Int
        get() {
            structure()
            return backing.size
        }

    /** The size, without registering a dependency. */
    public val untrackedSize: Int
        get() = backing.size

    override fun iterator(): MutableIterator<E> {
        structure()
        return backing.iterator()
    }

    override fun contains(element: E): Boolean = slotFor(element)()

    /** Whether [element] is present, without registering a dependency. */
    public fun containsUntracked(element: E): Boolean = element in backing

    override fun add(element: E): Boolean {
        if (!backing.add(element)) return false

        batch {
            slots[element]?.set(true)
            structure.update { it + 1 }
        }

        return true
    }

    override fun remove(element: E): Boolean {
        if (!backing.remove(element)) return false

        batch {
            slots[element]?.set(false)
            structure.update { it + 1 }
        }

        return true
    }

    override fun clear() {
        if (backing.isEmpty()) return

        val previous = backing.toList()
        backing.clear()
        batch {
            for (element in previous) slots[element]?.set(false)
            structure.update { it + 1 }
        }
    }

    private fun slotFor(element: E): MutableState<Boolean> {
        slots[element]?.let { return it }

        val slot = mutableStateOf(element in backing)
        slots[element] = slot
        return slot
    }
}

/** Creates a [ReactiveSet] holding [elements]. */
public fun <E> reactiveSetOf(vararg elements: E): ReactiveSet<E> = ReactiveSet(elements.toSet())

/** Creates a [ReactiveSet] holding the contents of [elements]. */
public fun <E> reactiveSetOf(elements: Set<E>): ReactiveSet<E> = ReactiveSet(elements)
