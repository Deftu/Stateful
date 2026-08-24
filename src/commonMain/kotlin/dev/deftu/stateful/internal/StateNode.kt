package dev.deftu.stateful.internal

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
internal class StateNode<T>(
    val kind: NodeKind,
    private var state: NodeState,
    private val equality: Equality<T>,
    private val compute: (() -> T)?,
    stored: Any?,
) {
    private var stored: Any? = stored

    // Allocated on first use. An effect never has dependents and a source never has dependencies,
    // so one of these is provably dead weight on every node of those kinds.
    private var dependencies: MutableList<StateNode<*>>? = null
    private var dependents: MutableList<StateNode<*>>? = null

    private fun dependencyList(): MutableList<StateNode<*>> =
        dependencies ?: ArrayList<StateNode<*>>().also { dependencies = it }

    private fun dependentList(): MutableList<StateNode<*>> =
        dependents ?: ArrayList<StateNode<*>>().also { dependents = it }

    /**
     * How many dependencies of the run in progress have matched the previous run positionally.
     *
     * `-1` when no run is in progress. A computation almost always reads the same dependencies in
     * the same order, so matching by position costs an identity comparison and no allocation. Only
     * a computation whose dependencies actually changed pays for [collectOverflow].
     */
    private var collectIndex: Int = -1

    /** Allocated only when a run diverges from the previous dependency order. */
    private var collectOverflow: MutableSet<StateNode<*>>? = null

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

    /** Whether a dispatch for this effect is already in flight. Assumes the lock is held. */
    val isDispatchPending: Boolean
        get() = dispatchPending

    val isDisposed: Boolean
        get() = StateGraph.locked { state == NodeState.Disposed }

    /** Resolves, then registers the currently running computation as a dependent. */
    fun readTracked(): T = StateGraph.locked {
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
    fun readUntracked(): T = StateGraph.locked { resolveAndRead() }

    @Suppress("UNCHECKED_CAST")
    private fun resolveAndRead(): T {
        if (state != NodeState.Clean && state != NodeState.Disposed) {
            resolve()
        }

        val value = stored
        check(value !== UNSET) {
            "A memo read its own value while computing it. This is usually a lambda closing over a " +
            "`var` that is reassigned to the memo itself, so the memo ends up depending on itself."
        }
        return value as T
    }

    private fun link(dependency: StateNode<*>) {
        if (collectIndex < 0) {
            attach(dependency)
            return
        }

        val current = dependencies
        if (collectOverflow == null) {
            if (current != null && collectIndex < current.size && current[collectIndex] === dependency) {
                collectIndex++
                return
            }

            val seen = LinkedHashSet<StateNode<*>>((current?.size ?: 0) + 4)
            if (current != null) {
                for (index in 0 until collectIndex) seen.add(current[index])
            }

            collectOverflow = seen
        }

        val seen = collectOverflow ?: return
        if (!seen.add(dependency)) return

        attach(dependency)
    }

    private fun attach(dependency: StateNode<*>) {
        val mine = dependencyList()
        if (mine.contains(dependency)) return

        mine.add(dependency)
        dependency.dependentList().add(this)
    }

    /**
     * Drops dependencies the finished run did not read, and ends collection.
     *
     * On the fast path the run matched a prefix of the previous set, so everything past that prefix
     * is stale and nothing needed to be recorded to know it.
     */
    private fun finishCollecting() {
        // An effect that writes the state it reads re-enters its own body, and the inner run ends
        // collection before the outer one unwinds. Nothing is left to prune at that point.
        if (collectIndex < 0) return

        val mine = dependencies
        if (mine != null) {
            val overflow = collectOverflow
            if (overflow == null) {
                while (mine.size > collectIndex) {
                    val stale = mine.removeAt(mine.size - 1)
                    stale.dependents?.remove(this)
                }
            } else {
                var index = 0
                while (index < mine.size) {
                    val dependency = mine[index]
                    if (dependency in overflow) {
                        index++
                    } else {
                        mine.removeAt(index)
                        dependency.dependents?.remove(this)
                    }
                }
            }
        }

        collectIndex = -1
        collectOverflow = null
    }

    /** Source only. Returns true when the write changed anything. */
    @Suppress("UNCHECKED_CAST")
    fun write(newValue: T): Boolean {
        if (state == NodeState.Disposed) return false

        val previous = stored
        if (previous !== UNSET && equality.areEqual(previous as T, newValue)) return false

        stored = newValue

        // Marking never mutates the list being walked, so the defensive copy these loops used to
        // make was pure allocation on the hottest write path.
        val watchers = dependents
        if (watchers != null) {
            for (index in watchers.indices) {
                watchers[index].markDirty()
            }
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
            StateGraph.enqueue(this)
        }
    }

    private fun markDirty() {
        mark(NodeState.Dirty)

        val watchers = dependents ?: return
        for (index in watchers.indices) {
            watchers[index].markCheck()
        }
    }

    private fun markCheck() {
        if (state != NodeState.Clean) return
        mark(NodeState.Check)

        val watchers = dependents ?: return
        for (index in watchers.indices) {
            watchers[index].markCheck()
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
            // Indexed rather than copied, and re-reading the size each turn, because resolving a
            // dependency can prune this node's own list.
            val mine = dependencies
            if (mine != null) {
                var index = 0
                while (index < mine.size) {
                    mine[index].resolve()
                    if (state == NodeState.Dirty) break
                    index++
                }
            }
        }

        val wasDirty = state == NodeState.Dirty
        if (kind == NodeKind.Effect) {
            // Leave the state alone: StateGraph.flush reads it to decide whether to dispatch.
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

        collectIndex = 0
        collectOverflow = null

        val newValue = try {
            tracking.withComputation(this) { compute() }
        } catch (throwable: Throwable) {
            collectIndex = -1
            collectOverflow = null
            throw throwable
        }

        if (state == NodeState.Disposed) {
            collectIndex = -1
            collectOverflow = null
            return
        }

        finishCollecting()

        val previous = stored
        if (previous === UNSET || !equality.areEqual(previous as T, newValue)) {
            stored = newValue

            val watchers = dependents
            if (watchers != null) {
                for (index in watchers.indices) {
                    watchers[index].mark(NodeState.Dirty)
                }
            }
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
        if (StateGraph.locked { state == NodeState.Disposed }) return

        val scope = scope
        scope?.reset()
        if (scope != null && scope.isDisposed) return

        StateGraph.locked {
            // Clearing here rather than at schedule time is what stops a redundant re-run. A write
            // that lands while a dispatch is queued marks the node again, and this run is about to
            // read that value anyway; only a write arriving *during* the body should schedule
            // another run, and those mark the node after this point.
            if (state != NodeState.Disposed) state = NodeState.Clean

            collectIndex = 0
            collectOverflow = null
        }

        try {
            tracking.withOwner(scope) {
                tracking.withComputation(this) { compute() }
            }
        } finally {
            StateGraph.locked {
                if (state == NodeState.Disposed) {
                    collectIndex = -1
                    collectOverflow = null
                } else {
                    finishCollecting()
                }
            }
        }
    }

    /**
     * Hands the body to this effect's scheduler. Assumes the lock is **not** held.
     *
     * Callers must check [isDispatchPending] first and leave the node queued if one is in flight.
     * Coalescing has to happen *before* the dirty state is consumed: a run dropped after
     * [takeIfDirty] has already marked the node clean is a change lost for good, not a change
     * merged into the pending run.
     *
     * The flag clears before the body runs, and the flush queue is drained again afterwards, so a
     * write that arrives mid-run is picked up rather than stranded.
     */
    fun dispatch() {
        StateGraph.locked { dispatchPending = true }
        scheduler.schedule(task)
    }

    /**
     * Built once per node rather than per dispatch.
     *
     * A fresh closure for every dispatch is an allocation on the path an effect-heavy consumer
     * takes on every frame, and this one captures nothing but the node itself.
     */
    private val task: () -> Unit = {
        StateGraph.locked { dispatchPending = false }
        try {
            runBody()
        } finally {
            StateGraph.flush()
        }
    }

    fun dispose() {
        StateGraph.locked {
            if (state == NodeState.Disposed) return@locked

            state = NodeState.Disposed

            dependencies?.let { mine ->
                for (index in mine.indices) mine[index].dependents?.remove(this)
            }
            dependencies = null

            dependents?.let { watchers ->
                for (index in watchers.indices) watchers[index].dependencies?.remove(this)
            }
            dependents = null

            collectIndex = -1
            collectOverflow = null
        }
    }
}
