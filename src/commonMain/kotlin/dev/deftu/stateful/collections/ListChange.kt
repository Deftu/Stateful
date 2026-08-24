package dev.deftu.stateful.collections

/**
 * A single structural or element-level edit applied to a [ReactiveList].
 *
 * Changes are emitted in apply order, as a list rather than a sequence so a consumer can inspect
 * a batch more than once.
 *
 * Ranges rather than single elements, so `addAll` is one change instead of *n*. [Move] is explicit
 * rather than a remove followed by an insert, because keyed reconciliation reuses a moved element's
 * derived state instead of tearing it down and rebuilding it.
 */
public sealed interface ListChange<out E> {
    /** [elements] were inserted starting at [index]. */
    public data class Insert<out E>(val index: Int, val elements: List<E>) : ListChange<E>

    /** [elements] were removed, having started at [index]. */
    public data class Remove<out E>(val index: Int, val elements: List<E>) : ListChange<E>

    /**
     * The element at [index] was replaced.
     *
     * [previous] is carried so a consumer can diff without keeping its own copy of the list.
     */
    public data class Set<out E>(val index: Int, val previous: E, val current: E) : ListChange<E>

    /** [element] moved from [from] to [to], with the elements between it shifting to compensate. */
    public data class Move<out E>(val from: Int, val to: Int, val element: E) : ListChange<E>

    /** Every element was removed at once. */
    public data class Clear<out E>(val previous: List<E>) : ListChange<E>
}
