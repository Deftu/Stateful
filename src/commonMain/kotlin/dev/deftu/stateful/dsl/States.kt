package dev.deftu.stateful.dsl

import dev.deftu.stateful.Disposable
import dev.deftu.stateful.Equality
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import dev.deftu.stateful.StateListener
import dev.deftu.stateful.Subscription
import dev.deftu.stateful.core.Node
import dev.deftu.stateful.core.NodeKind
import dev.deftu.stateful.core.NodeState
import dev.deftu.stateful.core.Runtime
import dev.deftu.stateful.core.UNSET
import dev.deftu.stateful.core.tracking

/**
 * Creates a writable state holding [value].
 *
 * [equality] decides what counts as a change. A write the equality considers equal does nothing:
 * no dependent is marked, no effect runs.
 */
public fun <T> mutableStateOf(
    value: T,
    equality: Equality<T> = Equality.structural(),
): MutableState<T> {
    return SourceState(Node(NodeKind.Source, NodeState.Clean, equality, null, value))
}

/**
 * Creates a state that never changes.
 *
 * Reads are free and register no dependency, tracked or not — there is nothing to depend on.
 */
public fun <T> stateOf(value: T): State<T> = ConstantState(value)

/**
 * Creates a lazily computed, cached state.
 *
 * [compute] runs on the first read and then only when a dependency it actually read has changed.
 * An unobserved memo never runs at all.
 *
 * Dependencies are re-collected on every run, so a branch not taken is not subscribed to.
 *
 * ### Contract
 *
 * [compute] **must be pure and cheap.** It runs under the runtime lock, so blocking inside it —
 * or taking another lock in the opposite order — stalls or deadlocks every thread using the
 * graph. Side effects belong in [effect], which runs with the lock released.
 */
public fun <T> memo(
    equality: Equality<T> = Equality.structural(),
    compute: () -> T,
): State<T> {
    return MemoState(Node(NodeKind.Memo, NodeState.Dirty, equality, compute, UNSET))
}

/**
 * Runs [block] immediately, and again whenever a state it read has changed.
 *
 * The body runs with the runtime lock released, so it may safely block, take other locks, or
 * touch another thread.
 *
 * Dispose the returned handle to stop it. Until ownership lands, an effect runs until disposed —
 * dropping the handle leaks it.
 */
public fun effect(block: () -> Unit): Disposable {
    val node = Node<Unit>(NodeKind.Effect, NodeState.Clean, Equality.never(), block, Unit)
    node.runBody()
    return NodeDisposable(node)
}

/**
 * Reads [block] without registering any dependency.
 *
 * The escape hatch for reading state inside a computation without making that computation depend
 * on it.
 */
public fun <T> untracked(block: () -> T): T = tracking.suppressing(block)

/**
 * Defers effect dispatch until the outermost [batch] returns.
 *
 * Writes apply immediately, so a read inside the block sees them; only the effects waiting on
 * those writes are held back, and each fires once no matter how many times its dependencies
 * changed inside the block. Nesting is by depth counter — only the outermost exit flushes.
 */
public fun <T> batch(block: () -> T): T = Runtime.batch(block)

/** Registers [listener] for the next change only, disposing before the listener is invoked. */
public fun <T> State<T>.subscribeOnce(listener: StateListener<T>): Subscription {
    lateinit var subscription: Subscription
    subscription = subscribe { value ->
        subscription.dispose()
        listener.onChanged(value)
    }

    return subscription
}

internal class SourceState<T>(private val node: Node<T>) : MutableState<T> {
    override fun invoke(): T = node.readTracked()

    override var value: T
        get() = node.readUntracked()
        set(value) = set(value)

    override fun set(value: T) {
        val changed = Runtime.locked { node.write(value) }
        if (changed) Runtime.flush()
    }

    override fun update(transform: (T) -> T) {
        set(transform(node.readUntracked()))
    }

    override fun subscribe(listener: StateListener<T>): Subscription = subscribeTo(this, listener)
}

internal class MemoState<T>(private val node: Node<T>) : State<T> {
    override fun invoke(): T = node.readTracked()

    override val value: T
        get() = node.readUntracked()

    override fun subscribe(listener: StateListener<T>): Subscription = subscribeTo(this, listener)
}

internal class ConstantState<T>(override val value: T) : State<T> {
    override fun invoke(): T = value

    override fun subscribe(listener: StateListener<T>): Subscription = DisposedSubscription
}

private object DisposedSubscription : Subscription {
    override val isDisposed: Boolean
        get() = true

    override fun dispose() {
        // A constant never changes, so there is nothing to stop listening to.
    }
}

private class NodeDisposable(private val node: Node<*>) : Subscription {
    override val isDisposed: Boolean
        get() = node.isDisposed

    override fun dispose() {
        node.dispose()
    }
}

private fun <T> subscribeTo(state: State<T>, listener: StateListener<T>): Subscription {
    var seenInitial = false
    val node = Node<Unit>(
        NodeKind.Effect,
        NodeState.Clean,
        Equality.never(),
        {
            val value = state()
            if (seenInitial) listener.onChanged(value) else seenInitial = true
        },
        Unit,
    )

    node.runBody()
    return NodeDisposable(node)
}

/** Factory form of [dev.deftu.stateful.map]. */
public fun <T, U> mappedStateOf(state: State<T>, mapper: (T) -> U): State<U> =
    memo { mapper(state()) }

/** Factory form of [dev.deftu.stateful.flatMap]. */
public fun <T, U> flatMappedStateOf(state: State<T>, mapper: (T) -> State<U>): State<U> =
    memo { mapper(state())() }

/** Factory form of [dev.deftu.stateful.zip]. */
public fun <A, B> zippedStateOf(first: State<A>, second: State<B>): State<Pair<A, B>> =
    memo { first() to second() }

/** Three-way [zippedStateOf]. */
public fun <A, B, C> zippedStateOf(
    first: State<A>,
    second: State<B>,
    third: State<C>,
): State<Triple<A, B, C>> = memo { Triple(first(), second(), third()) }

/** Factory form of [dev.deftu.stateful.combine]. */
public fun <A, B, R> combineStateOf(
    first: State<A>,
    second: State<B>,
    transform: (A, B) -> R,
): State<R> = memo { transform(first(), second()) }

/** Three-way [combineStateOf]. */
public fun <A, B, C, R> combineStateOf(
    first: State<A>,
    second: State<B>,
    third: State<C>,
    transform: (A, B, C) -> R,
): State<R> = memo { transform(first(), second(), third()) }
