package dev.deftu.stateful

/**
 * A registration made by [State.subscribe], ended by disposing it.
 *
 * Distinct from [Disposable] only in what it names: this is the whole lifetime of one listener, and
 * needs no enclosing owner. Disposing it from inside its own listener is safe.
 */
public interface Subscription : Disposable
