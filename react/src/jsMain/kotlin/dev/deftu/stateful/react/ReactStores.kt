package dev.deftu.stateful.react

import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.createOwner

/**
 * The shape React's `useSyncExternalStore` consumes: a way to subscribe, and a way to read.
 *
 * This is why [State.subscribe] is a core primitive rather than a legacy shim — the hook wants
 * exactly a subscription plus an untracked snapshot, which is the pair this library already has.
 */
@JsExport
public interface ExternalStore<T> {
    /**
     * Registers [onStoreChange] and returns a function that unregisters it.
     *
     * React is told only *that* something changed; it re-reads through [getSnapshot] itself. The
     * value is deliberately not passed, matching the hook's contract.
     */
    public fun subscribe(onStoreChange: () -> Unit): () -> Unit

    /**
     * Reads the current value, untracked.
     *
     * **Must be referentially stable between notifications.** React compares the result of
     * successive calls with `Object.is` and re-renders until two agree; a snapshot that allocates
     * a fresh object every call never converges and the component renders forever.
     *
     * This returns the state's stored value directly, so stability is the state's own. Derive with
     * [dev.deftu.stateful.dsl.memo] so the same instance comes back until the value genuinely
     * changes, and never feed a state built with [dev.deftu.stateful.Equality.never] to React —
     * it propagates on every write by definition, which here means an infinite render loop.
     */
    public fun getSnapshot(): T
}

/**
 * Exposes this state as a store for `useSyncExternalStore`.
 *
 * ```kotlin
 * val store = remember { someState.asExternalStore() }
 * val value = useSyncExternalStore(store::subscribe, store::getSnapshot)
 * ```
 *
 * Build the store once and hold it. Creating a new one on every render hands React a new
 * `subscribe` identity each time, which makes it tear down and re-establish the subscription on
 * every render.
 */
public fun <T> State<T>.asExternalStore(): ExternalStore<T> = StateExternalStore(this)

/**
 * Creates an [Owner] for React to dispose on unmount.
 *
 * ```kotlin
 * val root = useMemo({ statefulRoot() }, emptyArray())
 * useEffect({ { root.dispose() } }, emptyArray())
 * ```
 *
 * Kept as a plain function rather than a hook so this module needs no React dependency: a hook
 * would have to import React's own, and a Kotlin/JS library that hard-depends on React cannot be
 * used from anything else. The two lines above are the whole binding.
 *
 * Concurrent mode is safe here because the owner is created in `useMemo` and disposed in
 * `useEffect` cleanup, so a render that React discards never leaves a live owner behind — the
 * effect for it simply never runs, and the discarded owner holds nothing until something is created
 * under it.
 */
public fun statefulRoot(scheduler: Scheduler = Scheduler.Immediate): Owner = createOwner(scheduler)

private class StateExternalStore<T>(private val state: State<T>) : ExternalStore<T> {
    override fun subscribe(onStoreChange: () -> Unit): () -> Unit {
        val subscription = state.subscribe { onStoreChange() }
        return { subscription.dispose() }
    }

    override fun getSnapshot(): T = state.value
}
