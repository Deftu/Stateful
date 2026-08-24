package dev.deftu.stateful.collections

import dev.deftu.stateful.Equality
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.mutableStateOf

/**
 * A list whose reads are tracked per position, so a consumer of one index is not woken by an edit
 * to another.
 *
 * Two independent signals back it. Every position has a **slot** carrying the value at that index,
 * created on first tracked read of it; and one **structure** signal covers size and ordering.
 * Replacing an element touches its slot alone. A structural edit shifts values through the slots
 * it actually moved and bumps the structure signal.
 *
 * Reading [size], iterating, or any read that depends on the shape of the list registers on the
 * structure signal. Reading an index registers on that slot *and* on structure, because an index
 * that is valid now may not be after a removal.
 *
 * That last part is the coarse edge of this design: a structural edit wakes every positional
 * reader, not only the ones whose value changed. For list-shaped UI, where that cost is real, use
 * [mapKeyed] — it keys on identity rather than position and survives reordering without rebuilding.
 *
 * Not thread-safe for structural edits from several threads at once. The underlying signals are,
 * so reading concurrently with a write is safe; two concurrent writers are not.
 */
public class ReactiveList<E> internal constructor(initial: List<E>) : AbstractMutableList<E>() {
    private val backing = ArrayList(initial)
    private val slots = ArrayList<MutableState<E>?>(List(initial.size) { null })
    private val structure = mutableStateOf(0, Equality.never())
    private val changeLog = mutableStateOf<List<ListChange<E>>>(emptyList(), Equality.never())

    /**
     * The most recent batch of changes.
     *
     * Reading this tracked wakes the reader on every edit. It is the stream keyed reconciliation
     * consumes, and is more useful than diffing two snapshots.
     */
    public val changes: State<List<ListChange<E>>>
        get() = changeLog

    override val size: Int
        get() {
            structure()
            return backing.size
        }

    /** The size, without registering a dependency. */
    public val untrackedSize: Int
        get() = backing.size

    override fun get(index: Int): E {
        structure()
        if (index !in backing.indices) {
            throw IndexOutOfBoundsException("index $index, size ${backing.size}")
        }

        return slotAt(index)()
    }

    /** Reads the element at [index] without registering a dependency. */
    public fun getUntracked(index: Int): E = backing[index]

    override fun set(index: Int, element: E): E {
        val previous = backing.set(index, element)
        slots.getOrNull(index)?.set(element)
        emit(ListChange.Set(index, previous, element))
        return previous
    }

    override fun add(index: Int, element: E) {
        backing.add(index, element)
        slots.add(index, null)
        reslot(index)
        bump(ListChange.Insert(index, listOf(element)))
    }

    override fun addAll(elements: Collection<E>): Boolean = addAll(backing.size, elements)

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return false

        backing.addAll(index, elements)
        repeat(elements.size) { offset -> slots.add(index + offset, null) }
        reslot(index)
        bump(ListChange.Insert(index, elements.toList()))
        return true
    }

    override fun removeAt(index: Int): E {
        val removed = backing.removeAt(index)
        slots.removeAt(index)
        reslot(index)
        bump(ListChange.Remove(index, listOf(removed)))
        return removed
    }

    override fun clear() {
        if (backing.isEmpty()) return

        val previous = backing.toList()
        backing.clear()
        slots.clear()
        bump(ListChange.Clear(previous))
    }

    /**
     * Moves the element at [from] to [to], shifting the elements between them.
     *
     * Emits a single [ListChange.Move] rather than a removal and an insertion, so a consumer doing
     * keyed reconciliation can reuse the element's derived state instead of rebuilding it.
     */
    public fun move(from: Int, to: Int) {
        if (from == to) return

        val element = backing.removeAt(from)
        backing.add(to, element)
        val slot = slots.removeAt(from)
        slots.add(to, slot)
        reslot(minOf(from, to))
        bump(ListChange.Move(from, to, element))
    }

    /** Replaces the whole contents in one batch, emitting a clear followed by an insert. */
    public fun replaceAll(elements: List<E>) {
        batch {
            val previous = backing.toList()
            backing.clear()
            backing.addAll(elements)

            while (slots.size > elements.size) slots.removeAt(slots.size - 1)
            while (slots.size < elements.size) slots.add(null)
            reslot(0)

            val changes = buildList {
                if (previous.isNotEmpty()) add(ListChange.Clear(previous))
                if (elements.isNotEmpty()) add(ListChange.Insert(0, elements.toList()))
            }
            structure.update { it + 1 }
            changeLog.set(changes)
        }
    }

    private fun slotAt(index: Int): MutableState<E> {
        slots[index]?.let { return it }

        val slot = mutableStateOf(backing[index])
        slots[index] = slot
        return slot
    }

    /**
     * Pushes backing values into the slots from [start] onward.
     *
     * Slots are positional: after a shift, the slot at a given index must carry whatever now lives
     * at that index. Slots nothing has read yet stay null and cost nothing.
     */
    private fun reslot(start: Int) {
        for (index in start until backing.size) {
            slots[index]?.set(backing[index])
        }
    }

    private fun bump(change: ListChange<E>) {
        batch {
            structure.update { it + 1 }
            changeLog.set(listOf(change))
        }
    }

    private fun emit(change: ListChange<E>) {
        changeLog.set(listOf(change))
    }
}

/** Creates a [ReactiveList] holding [elements]. */
public fun <E> reactiveListOf(vararg elements: E): ReactiveList<E> = ReactiveList(elements.toList())

/** Creates a [ReactiveList] holding the contents of [elements]. */
public fun <E> reactiveListOf(elements: List<E>): ReactiveList<E> = ReactiveList(elements)
