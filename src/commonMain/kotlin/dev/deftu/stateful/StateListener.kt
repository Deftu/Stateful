package dev.deftu.stateful

/**
 * Called with the new value whenever a subscribed state changes.
 *
 * Contravariant so that a listener accepting a supertype can subscribe to a `State<out T>`.
 */
public fun interface StateListener<in T> {
    public fun onChanged(value: T)
}
