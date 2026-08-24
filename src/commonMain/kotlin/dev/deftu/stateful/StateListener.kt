package dev.deftu.stateful

/**
 * Called with the new value whenever a subscribed state changes.
 *
 * Contravariant so that a listener accepting a supertype can subscribe to a `State<out T>`.
 */
public fun interface StateListener<in T> {
    /**
     * Called after the graph has settled and the runtime lock is released, so [value] is never
     * a torn or half-propagated read.
     */
    public fun onChanged(value: T)
}
