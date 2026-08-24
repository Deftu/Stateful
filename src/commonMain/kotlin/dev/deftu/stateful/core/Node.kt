package dev.deftu.stateful.core

import dev.deftu.stateful.Equality
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler

internal enum class NodeKind {
    /** A manually written value. No dependencies, no compute function. */
    Source,

    /** A lazily computed, cached value. Recomputes only when read. */
    Memo,

    /** A side effect. Not lazy: runs whenever its dependencies change. Produces no value. */
    Effect,
}

/**
 * Ordering is load-bearing: [Node.mark] only ever raises a node's state, which is what stops a
 * write from re-walking a subgraph it has already marked.
 */
internal enum class NodeState {
    /** Value is current. */
    Clean,

    /** Some transitive dependency may have changed; resolve before trusting the value. */
    Check,

    /** A direct dependency definitely changed; must recompute. */
    Dirty,

    /** Terminal. Never recomputes, never propagates. */
    Disposed,
}

/** Distinguishes "no value yet" from a legitimately stored `null`. */
internal val UNSET: Any = Any()

/**
 * One node of the dependency graph.
 *
 * Every method here assumes the runtime lock is held unless it says otherwise. [runBody] is the
 * exception: it deliberately runs user effect code with the lock released, reacquiring it only to
 * swap the dependency set.
 */
internal class Node<T>(
    val kind: NodeKind,
    private var state: NodeState,
    private val equality: Equality<T>,
    private val compute: (() -> T)?,
    stored: Any?,
) {
    private var stored: Any? = stored

    private val dependencies = mutableListOf<Node<*>>()
    private val dependents = mutableListOf<Node<*>>()

    /** Dependencies seen during the run in progress. Non-null only while recomputing. */
    private var collecting: MutableSet<Node<*>>? = null

    /**
     * Effects only: the owner that computations created inside the body belong to.
     *
     * Reset before every run, so anything the previous run created — nested effects, cleanups —
     * is torn down before the next one starts.
     */
    var scope: Owner? = null

    /** Effects only. Where the body is dispatched. */
    var scheduler: Scheduler = Scheduler.Immediate

    private var dispatchPending = false

    val isDisposed: Boolean
        get() = Runtime.locked { state == NodeState.Disposed }

    /** Resolves, then registers the currently running computation as a dependent. */
    fun readTracked(): T = Runtime.locked {
        // Resolve before linking. If this node is dirty, resolving marks its dependents dirty —
        // and a dependent added first would be marked without ever having seen the old value.
        val value = resolveAndRead()

        val context = tracking
        val dependent = context.computation
        if (dependent != null && !context.suppressed && dependent !== this && dependent.state != NodeState.Disposed) {
            dependent.link(this)
        }

        value
    }

    /** Resolves and reads without touching the graph. */
    fun readUntracked(): T = Runtime.locked { resolveAndRead() }

    @Suppress("UNCHECKED_CAST")
    private fun resolveAndRead(): T {
        if (state != NodeState.Clean && state != NodeState.Disposed) {
            resolve()
        }

        val value = stored
        check(value !== UNSET) { "Node read before it held a value" }
        return value as T
    }

    private fun link(dependency: Node<*>) {
        val seen = collecting
        if (seen != null && !seen.add(dependency)) return

        if (!dependencies.contains(dependency)) {
            dependencies.add(dependency)
            dependency.dependents.add(this)
        }
    }

    /** Source only. Returns true when the write changed anything. */
    @Suppress("UNCHECKED_CAST")
    fun write(newValue: T): Boolean {
        if (state == NodeState.Disposed) return false

        val previous = stored
        if (previous !== UNSET && equality.areEqual(previous as T, newValue)) return false

        stored = newValue
        for (dependent in dependents.toList()) {
            dependent.markDirty()
        }

        return true
    }

    private fun mark(newState: NodeState) {
        if (state.ordinal >= newState.ordinal) return
        state = newState

        // Only effects are queued, and they are queued on Check as well as Dirty: an effect is
        // where a pull has to start, and a memo between it and the source leaves it merely
        // Check. Memos are never queued, which is what makes an unobserved memo cost nothing —
        // it stays dirty until something actually reads it.
        if (kind == NodeKind.Effect) {
            Runtime.enqueue(this)
        }
    }

    private fun markDirty() {
        mark(NodeState.Dirty)
        for (dependent in dependents.toList()) {
            dependent.markCheck()
        }
    }

    private fun markCheck() {
        if (state != NodeState.Clean) return
        mark(NodeState.Check)
        for (dependent in dependents.toList()) {
            dependent.markCheck()
        }
    }

    /**
     * Brings this node up to date, recomputing if a dependency actually changed.
     *
     * For a [NodeKind.Effect] this resolves dependencies and leaves the node dirty for the
     * runtime to dispatch — an effect body must not run under the lock.
     */
    fun resolve() {
        if (state == NodeState.Clean || state == NodeState.Disposed) return

        if (state == NodeState.Check) {
            for (dependency in dependencies.toList()) {
                dependency.resolve()
                if (state == NodeState.Dirty) break
            }
        }

        val wasDirty = state == NodeState.Dirty
        if (kind == NodeKind.Effect) {
            // Leave the state alone: Runtime.flush reads it to decide whether to dispatch.
            if (!wasDirty) state = NodeState.Clean
            return
        }

        state = NodeState.Clean
        if (wasDirty) recompute()
    }

    /** Memo recomputation. Runs the compute function under the lock, by contract. */
    @Suppress("UNCHECKED_CAST")
    private fun recompute() {
        val compute = compute ?: return

        val seen = LinkedHashSet<Node<*>>()
        collecting = seen

        val newValue = try {
            tracking.withComputation(this) { compute() }
        } finally {
            collecting = null
        }

        if (state == NodeState.Disposed) return
        pruneDependencies(seen)

        val previous = stored
        if (previous === UNSET || !equality.areEqual(previous as T, newValue)) {
            stored = newValue
            for (dependent in dependents.toList()) {
                dependent.mark(NodeState.Dirty)
            }
        }
    }

    private fun pruneDependencies(seen: Set<Node<*>>) {
        val stale = dependencies.filter { dependency -> dependency !in seen }
        for (dependency in stale) {
            dependencies.remove(dependency)
            dependency.dependents.remove(this)
        }
    }

    /** Whether [runBody] must be called. Assumes the lock is held. */
    fun takeIfDirty(): Boolean {
        if (state == NodeState.Disposed) return false
        resolve()

        val dirty = state == NodeState.Dirty
        if (dirty) state = NodeState.Clean
        return dirty
    }

    /**
     * Runs an effect body **with the lock released**, so user code cannot deadlock the graph.
     *
     * Tracked reads inside the body reacquire the lock individually, which is safe: edges stay
     * live throughout, so a concurrent write still finds this node. The dependency set is swapped
     * atomically once the body returns.
     */
    fun runBody() {
        val compute = compute ?: return
        if (Runtime.locked { state == NodeState.Disposed }) return

        val scope = scope
        scope?.reset()
        if (scope != null && scope.isDisposed) return

        val seen = LinkedHashSet<Node<*>>()
        Runtime.locked { collecting = seen }

        try {
            tracking.withOwner(scope) {
                tracking.withComputation(this) { compute() }
            }
        } finally {
            Runtime.locked {
                collecting = null
                if (state != NodeState.Disposed) pruneDependencies(seen)
            }
        }
    }

    /**
     * Hands the body to this effect's scheduler. Assumes the lock is **not** held.
     *
     * A second dispatch while one is still pending is dropped. Under a deferring scheduler, ten
     * writes before a drain would otherwise queue ten runs of the same effect, every one of them
     * reading the same final value — coalescing them is what the scheduler is for. The flag clears
     * before the body runs, so a write from inside the body still schedules the next run.
     */
    fun dispatch() {
        val shouldDispatch = Runtime.locked {
            if (dispatchPending) return@locked false

            dispatchPending = true
            true
        }
        if (!shouldDispatch) return

        scheduler.schedule {
            Runtime.locked { dispatchPending = false }
            runBody()
        }
    }

    fun dispose() {
        Runtime.locked {
            if (state == NodeState.Disposed) return@locked

            state = NodeState.Disposed
            for (dependency in dependencies.toList()) {
                dependency.dependents.remove(this)
            }
            dependencies.clear()

            for (dependent in dependents.toList()) {
                dependent.dependencies.remove(this)
            }
            dependents.clear()
            collecting = null
        }
    }
}
