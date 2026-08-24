package dev.deftu.stateful.collections

import dev.deftu.stateful.Equality
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.dsl.batch
import dev.deftu.stateful.dsl.mutableStateOf

/**
 * A map whose reads are tracked per key, so a consumer of one entry is not woken by an edit to
 * another.
 *
 * Every key has a slot, created on first tracked read. Reading a key that is **absent** still
 * registers on its slot, so a later insert wakes the reader — without that, the common shape of
 * "render nothing until this arrives" would never update.
 *
 * Reading [size] or any of the collection views registers on the structure signal instead, which
 * changes when a key is added or removed but not when an existing key's value is replaced.
 *
 * Not thread-safe for concurrent structural edits. Reads are.
 */
public class ReactiveMap<K, V> internal constructor(initial: Map<K, V>) : AbstractMutableMap<K, V>() {
    private val backing = LinkedHashMap(initial)
    private val slots = HashMap<K, MutableState<V?>>()
    private val structure = mutableStateOf(0, Equality.never())

    override val size: Int
        get() {
            structure()
            return backing.size
        }

    /** The size, without registering a dependency. */
    public val untrackedSize: Int
        get() = backing.size

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            structure()
            return backing.entries
        }

    override fun get(key: K): V? {
        return slotFor(key)()
    }

    /** Reads [key] without registering a dependency. */
    public fun getUntracked(key: K): V? = backing[key]

    /** Whether [key] is present, registering a dependency on that key. */
    override fun containsKey(key: K): Boolean = slotFor(key)() != null

    override fun put(key: K, value: V): V? {
        val existed = backing.containsKey(key)
        val previous = backing.put(key, value)

        if (existed) {
            slots[key]?.set(value)
        } else {
            batch {
                slots[key]?.set(value)
                structure.update { it + 1 }
            }
        }

        return previous
    }

    override fun remove(key: K): V? {
        if (!backing.containsKey(key)) return null

        val previous = backing.remove(key)
        batch {
            slots[key]?.set(null)
            structure.update { it + 1 }
        }

        return previous
    }

    override fun clear() {
        if (backing.isEmpty()) return

        val keys = backing.keys.toList()
        backing.clear()
        batch {
            for (key in keys) slots[key]?.set(null)
            structure.update { it + 1 }
        }
    }

    private fun slotFor(key: K): MutableState<V?> {
        slots[key]?.let { return it }

        val slot = mutableStateOf(backing[key])
        slots[key] = slot
        return slot
    }
}

/** Creates a [ReactiveMap] holding [pairs]. */
public fun <K, V> reactiveMapOf(vararg pairs: Pair<K, V>): ReactiveMap<K, V> =
    ReactiveMap(pairs.toMap())

/** Creates a [ReactiveMap] holding the contents of [entries]. */
public fun <K, V> reactiveMapOf(entries: Map<K, V>): ReactiveMap<K, V> = ReactiveMap(entries)
