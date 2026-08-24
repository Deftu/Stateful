package dev.deftu.stateful.dsl

import dev.deftu.stateful.Disposable
import dev.deftu.stateful.Equality
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.State
import dev.deftu.stateful.StateListener
import dev.deftu.stateful.Subscription
import dev.deftu.stateful.core.Node
import dev.deftu.stateful.core.NodeKind
import dev.deftu.stateful.core.NodeState
import dev.deftu.stateful.core.Runtime
import dev.deftu.stateful.core.UNSET
import dev.deftu.stateful.core.tracking
import dev.deftu.stateful.core.warn

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
 * Creates a writable state that reacts to **replacement only**, compared by identity.
 *
 * The escape hatch for large or expensive-to-compare payloads. A structural comparison of a big
 * immutable blob on every write costs as much as the work the graph is trying to avoid, so this
 * compares references instead: assigning a different instance propagates, assigning the same
 * instance does not, and mutating the value in place is invisible.
 *
 * Nothing here tracks the contents of a value, so this differs from [mutableStateOf] only in the
 * cost and the meaning of a write — it is not a deep/shallow switch. Reach for a reactive
 * collection when the contents themselves need to be observed.
 */
public fun <T> rawStateOf(value: T): MutableState<T> =
    mutableStateOf(value, Equality.referential())

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
 * Creates a derived state that recomputes on **every** read and caches nothing.
 *
 * There is no graph node behind this: [compute] runs in the caller's tracking context, so a
 * tracked read of the result registers dependencies on whatever [compute] itself read. It is a
 * lambda with a `State` interface on it, and costs about that much.
 *
 * Use it when [compute] is cheaper than the node [memo] would allocate — `state() + 1`, a field
 * access, a string comparison. Use [memo] when the computation is expensive, or when the
 * **equality cutoff matters**: a memo whose value is unchanged stops propagation dead, and this
 * does not. Deriving `name().isNotBlank()` with this wakes dependents on every keystroke; deriving
 * it with [memo] wakes them only when the answer flips.
 *
 * That cutoff is usually why an operator exists, which is why [map], [dev.deftu.stateful.combine]
 * and everything in `ext` build memos rather than these.
 */
public fun <T> derivedStateOf(compute: () -> T): State<T> = DerivedState(compute)

/**
 * Runs [block] once, and again whenever a state it read has changed.
 *
 * Every run — the first one included — goes through the owning root's [Scheduler] with the
 * runtime lock released, so the body may safely block, take other locks, or touch another thread.
 * Under [Scheduler.Immediate], the default, that means the first run happens before this function
 * returns; under a deferring scheduler the effect has not run and has no dependencies until the
 * scheduler gets to it.
 *
 * The effect belongs to the enclosing [createRoot] and dies with it. Computations created inside
 * the body belong to the effect, and are torn down before each re-run — which is what makes
 * [onCleanup] inside an effect meaningful.
 *
 * Creating an effect outside any root is almost always a leak, since nothing but the returned
 * handle can ever stop it. That case is not fatal: the effect attaches to a process-wide root and
 * a warning is logged naming it.
 */
public fun effect(block: () -> Unit): Disposable {
    val owner = tracking.owner ?: orphanRoot().also {
        warn("effect created outside of createRoot; it will run until disposed by hand, which is probably a leak")
    }

    val node = Node<Unit>(NodeKind.Effect, NodeState.Clean, Equality.never(), block, Unit)
    node.scheduler = owner.scheduler
    node.scope = owner.child()
    owner.register(node)

    node.dispatch()
    return NodeDisposable(node)
}

/**
 * Creates a lifetime for the computations made inside [block], and hands it to [block] so it can
 * be disposed later.
 *
 * The [Owner] itself is passed rather than a bare [Disposable], so the block can also ask what the
 * owner is holding — which is what a leak assertion needs.
 *
 * Disposing the owner tears down every effect and nested owner created under it, depth-first,
 * running cleanups in reverse creation order. One root per screen or component, disposed on
 * unmount, is the intended shape.
 *
 * [scheduler] applies to every effect under this root.
 *
 * A root created inside another root is **detached** — it is not a child, and the outer root will
 * not dispose it. Roots are independent lifetimes by definition; nest [effect]s, not roots, when
 * you want automatic teardown.
 */
public fun <T> createRoot(scheduler: Scheduler = Scheduler.Immediate, block: (Owner) -> T): T {
    val owner = Owner(scheduler)
    return tracking.withOwner(owner) { block(owner) }
}

/**
 * Creates a lifetime without entering it, for hosts that hand you callbacks instead of a scope.
 *
 * Ktor, Spring, a Minecraft mod and an Android `Activity` all start something in one stack frame
 * and stop it in another, so there is no block to wrap. Store the returned [Owner] on the host
 * object, enter it with [runWithOwner] from later callbacks, and dispose it in the host's teardown
 * hook.
 *
 * The root's lifetime must be the host object's lifetime. A root that outlives its host is the
 * leak the orphan warning describes; one that dies early leaves state that silently stops
 * updating.
 *
 * A [scheduler] is usually mandatory rather than optional in a host: server frameworks call in on
 * request threads and game clients must be touched on their own thread, and [Scheduler.Immediate]
 * runs the body wherever the write happened.
 */
public fun createOwner(scheduler: Scheduler = Scheduler.Immediate): Owner = Owner(scheduler)

/**
 * Runs [block] with [owner] as the enclosing lifetime.
 *
 * The current owner is thread-local and does not survive between callbacks, so this is how a
 * computation created in a later callback attaches to a root created in an earlier one.
 *
 * This **borrows** a lifetime, it does not create one. Computations created inside live until
 * [owner] is disposed, not until the block returns — which is the point, and also the way to
 * misuse it. A per-request callback that creates an effect on an application-lifetime root will
 * accumulate effects forever; give a request its own short-lived root instead.
 */
public fun <T> runWithOwner(owner: Owner, block: () -> T): T = tracking.withOwner(owner, block)

/**
 * Registers [block] to run when the enclosing owner is disposed, and — inside an [effect] — before
 * each re-run of that effect.
 *
 * Cleanups run in reverse creation order, with the runtime lock released.
 *
 * Called outside any owner this does nothing but warn: there is no lifetime to attach to, so the
 * cleanup could never fire.
 */
public fun onCleanup(block: () -> Unit) {
    val owner = tracking.owner
    if (owner == null) {
        warn("onCleanup called outside of createRoot or effect; it will never run")
        return
    }

    owner.addCleanup(block)
}

private var orphanRootInstance: Owner? = null

private fun orphanRoot(): Owner {
    return Runtime.locked {
        orphanRootInstance ?: Owner(Scheduler.Immediate).also { orphanRootInstance = it }
    }
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

internal class DerivedState<T>(private val compute: () -> T) : State<T> {
    override fun invoke(): T = compute()

    override val value: T
        get() = untracked(compute)

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
