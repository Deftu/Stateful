package dev.deftu.stateful.svelte

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State

/**
 * The readable half of the Svelte store contract.
 *
 * Svelte's `$store` syntax is duck-typed: anything with a `subscribe` taking a function and
 * returning an unsubscribe function works, in Svelte 3, 4 and 5 alike. That is why this is the
 * target rather than runes — a rune cannot wrap state that lives outside the component, and
 * `$state` is compiler magic applied to declarations, not a wrapper.
 */
@JsExport
public interface Readable<T> {
    /**
     * Registers [run] and returns a function that unregisters it.
     *
     * Per the contract, [run] is called **immediately** with the current value before this returns.
     */
    public fun subscribe(run: (T) -> Unit): () -> Unit
}

/** The writable half of the Svelte store contract: [Readable] plus [set] and [update]. */
@JsExport
public interface Writable<T> : Readable<T> {
    /** Replaces the value. */
    public fun set(value: T)

    /** Replaces the value with the result of [updater] applied to the current one. */
    public fun update(updater: (T) -> T)
}

/**
 * Exposes this state as a Svelte store, usable with `$store` syntax.
 *
 * The contract requires the subscriber to be called immediately with the current value.
 * [State.subscribe] deliberately fires on change only, so the immediate call is synthesised here
 * from [State.value]. Omitting it renders a component blank until the first change, which is the
 * mistake this adapter exists to prevent anyone making by hand.
 *
 * The returned unsubscribe function is the whole lifetime. No owner is involved, which matches
 * Svelte: the component's teardown calls it.
 */
public fun <T> State<T>.asSvelteStore(): Readable<T> = ReadableStore(this)

/**
 * Exposes this mutable state as a writable Svelte store.
 *
 * `set` and `update` write straight through, so a `bind:value` in a Svelte component drives the
 * Stateful source directly.
 */
public fun <T> MutableState<T>.asSvelteStore(): Writable<T> = WritableStore(this)

private class ReadableStore<T>(private val state: State<T>) : Readable<T> {
    override fun subscribe(run: (T) -> Unit): () -> Unit {
        run(state.value)
        val subscription = state.subscribe { value -> run(value) }
        return { subscription.dispose() }
    }
}

private class WritableStore<T>(private val state: MutableState<T>) : Writable<T> {
    override fun subscribe(run: (T) -> Unit): () -> Unit {
        run(state.value)
        val subscription = state.subscribe { value -> run(value) }
        return { subscription.dispose() }
    }

    override fun set(value: T) {
        state.set(value)
    }

    override fun update(updater: (T) -> T) {
        state.update(updater)
    }
}
