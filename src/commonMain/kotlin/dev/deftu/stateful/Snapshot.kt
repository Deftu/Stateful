package dev.deftu.stateful

/**
 * Reads [state] untracked and returns a plain, non-reactive copy of its value.
 *
 * For handing a value to something that must not hold a live reference into the graph:
 * serialisation, logging, a snapshot for an undo stack, a payload crossing a thread boundary.
 *
 * ### What "plain" means
 *
 * This removes **reactivity**, not mutability. Nested states are unwrapped to their values, and
 * collections are copied into ordinary read-only ones so that later graph activity cannot change
 * what you are holding. Anything else — your own data classes, arrays, platform types — is
 * returned as it is. Common Kotlin has no reflection, so there is no way to clone an arbitrary
 * object, and pretending otherwise would be worse than saying so.
 *
 * A value made of data classes, primitives and collections comes back fully detached. A value
 * holding a mutable object of your own comes back sharing that object.
 *
 * ### Types
 *
 * A reactive or mutable collection comes back as the plain read-only equivalent, so a `T` declared
 * as `List<E>`, `Set<E>` or `Map<K, V>` is preserved exactly. A `T` declared as a *concrete*
 * collection type — `ArrayList<E>`, or a reactive collection type itself — is not what comes back,
 * and casting the result to it will fail. Declare such states with the read-only interface.
 */
public fun <T> snapshot(state: State<T>): T {
    @Suppress("UNCHECKED_CAST")
    return detach(state.value) as T
}

private fun detach(value: Any?): Any? = when (value) {
    is State<*> -> detach(value.value)
    is List<*> -> value.map(::detach)
    is Set<*> -> value.mapTo(LinkedHashSet(value.size)) { element -> detach(element) }
    is Map<*, *> -> value.entries.associate { (key, entry) -> detach(key) to detach(entry) }
    else -> value
}
