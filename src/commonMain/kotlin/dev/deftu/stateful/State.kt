package dev.deftu.stateful

/**
 * A value that can be read, observed, and derived from.
 *
 * Evaluation is pull-based: writing to a source does not compute anything, it marks dependents
 * as needing work. Values are computed when they are read, which is what makes derived state
 * both lazy and glitch-free — a memo over a diamond recomputes once, after every branch has
 * settled, never with a half-updated view.
 */
public interface State<out T> {
    /**
     * Reads the value **and registers a dependency** on it.
     *
     * Inside a [memo] or [effect], this is what makes the enclosing computation re-run when this
     * state changes. Outside any computation it is simply a read.
     *
     * Dependencies are re-collected on every recomputation, so a branch that is not taken does
     * not subscribe. This is the read you want almost everywhere.
     *
     * ### Coming from Compose
     *
     * **This is the opposite of Compose.** Compose's `State.value` is the tracked read; here
     * [value] is the *untracked* one and this is the tracked one. Reading [value] where you meant
     * to track produces silently non-reactive code with no compile error. When in doubt, use
     * `state()`.
     *
     * @see value for the untracked read.
     */
    public operator fun invoke(): T

    /**
     * Reads the value **without registering a dependency**.
     *
     * Pure data access: no edge is created, and a computation reading this will not re-run when
     * it changes. Use it for one-off comparisons, logging, and handing a value to code that has
     * nothing to do with the graph.
     *
     * ### Coming from Compose
     *
     * **This is the opposite of Compose.** In Compose, reading `.value` is how you subscribe. In
     * Stateful, reading `.value` is how you *avoid* subscribing — a tracked read is `state()`.
     * A Compose habit applied here compiles cleanly and quietly never updates.
     *
     * @see invoke for the tracked read.
     */
    public val value: T

    /**
     * Registers [listener] to be called whenever the value changes.
     *
     * Fires on change only — not on subscription. The listener runs after the graph is fully
     * consistent and the runtime lock has been released, so it never observes a torn value.
     *
     * Needs no enclosing owner: the returned [Subscription] is the whole lifetime. Disposing it
     * from inside the listener itself is safe.
     */
    public fun subscribe(listener: StateListener<T>): Subscription
}

/** A [State] whose value can be written. */
public interface MutableState<T> : State<T> {
    /**
     * Reads without registering a dependency, and writes.
     *
     * See [State.value] for why this read is the untracked one, and why that is the reverse of
     * Compose.
     */
    override var value: T

    /** Writes [value]. A write equal to the current value, per the state's equality, does nothing. */
    public fun set(value: T)

    /**
     * Applies [transform] to the current value and writes the result.
     *
     * The read of the current value is untracked, so calling this inside a computation does not
     * make that computation depend on this state.
     */
    public fun update(transform: (T) -> T)
}
