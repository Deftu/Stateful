package dev.deftu.stateful

import dev.deftu.stateful.internal.StateNode
import dev.deftu.stateful.internal.StateGraph

/**
 * The lifetime a set of computations belongs to.
 *
 * Every computation runs under an owner, and computations created inside one become its children.
 * Disposing an owner tears down the whole subtree, so a screen or component can be unmounted by
 * disposing a single handle rather than by tracking every effect it created.
 *
 * Obtained from [dev.deftu.stateful.dsl.createRoot]; not constructed directly.
 */
public class Owner internal constructor(
    internal val scheduler: Scheduler,
) : Disposable {
    private val children = mutableListOf<Owner>()
    private val cleanups = mutableListOf<() -> Unit>()
    private val nodes = mutableListOf<StateNode<*>>()
    private var disposed = false

    override val isDisposed: Boolean
        get() = StateGraph.locked { disposed }

    /**
     * How many child owners this owner holds.
     *
     * Present so a consumer can write a leak assertion. Failing to dispose an owner is this
     * library's worst failure mode — it is silent, and it compounds — and without a way to count
     * what an owner is holding, that failure cannot be tested from outside this module.
     */
    public val childCount: Int
        get() = StateGraph.locked { children.size }

    /** How many cleanups are registered on this owner. See [childCount]. */
    public val cleanupCount: Int
        get() = StateGraph.locked { cleanups.size }

    /** How many computations belong directly to this owner. See [childCount]. */
    public val computationCount: Int
        get() = StateGraph.locked { nodes.size }

    /** Whether this owner holds nothing: no children, no cleanups, no computations. */
    public val isEmpty: Boolean
        get() = StateGraph.locked { children.isEmpty() && cleanups.isEmpty() && nodes.isEmpty() }

    internal fun child(): Owner? = StateGraph.locked {
        if (disposed) return@locked null

        val child = Owner(scheduler)
        children.add(child)
        child
    }

    internal fun register(node: StateNode<*>) {
        StateGraph.locked {
            if (disposed) return@locked
            nodes.add(node)
        }
    }

    internal fun addCleanup(block: () -> Unit) {
        StateGraph.locked {
            if (disposed) return@locked
            cleanups.add(block)
        }
    }

    /**
     * Tears down everything created under this owner without disposing the owner itself, so it
     * can be filled again.
     *
     * This is what makes `onCleanup` inside an effect useful: the previous run's cleanups fire
     * before the next run starts.
     */
    internal fun reset() {
        val (takenChildren, takenCleanups, takenNodes) = StateGraph.locked {
            if (disposed) return@locked Triple(emptyList<Owner>(), emptyList<() -> Unit>(), emptyList<StateNode<*>>())

            val snapshot = Triple(children.toList(), cleanups.toList(), nodes.toList())
            children.clear()
            cleanups.clear()
            nodes.clear()
            snapshot
        }

        tearDown(takenChildren, takenCleanups, takenNodes)
    }

    /**
     * Disposes the subtree depth-first: children in reverse creation order, then this owner's own
     * cleanups in reverse creation order, then its nodes.
     *
     * Reverse order matters — a cleanup usually undoes something a later one depends on, so
     * unwinding in creation order would tear down a dependency before its dependent.
     */
    override fun dispose() {
        val (takenChildren, takenCleanups, takenNodes) = StateGraph.locked {
            if (disposed) return@locked null

            disposed = true
            val snapshot = Triple(children.toList(), cleanups.toList(), nodes.toList())
            children.clear()
            cleanups.clear()
            nodes.clear()
            snapshot
        } ?: return

        tearDown(takenChildren, takenCleanups, takenNodes)
    }

    private fun tearDown(
        takenChildren: List<Owner>,
        takenCleanups: List<() -> Unit>,
        takenNodes: List<StateNode<*>>,
    ) {
        var failure: Throwable? = null

        for (child in takenChildren.asReversed()) {
            try {
                child.dispose()
            } catch (throwable: Throwable) {
                failure = failure?.also { it.addSuppressed(throwable) } ?: throwable
            }
        }

        // Cleanups are user code and run with the lock released, same as effect bodies.
        for (cleanup in takenCleanups.asReversed()) {
            try {
                cleanup()
            } catch (throwable: Throwable) {
                failure = failure?.also { it.addSuppressed(throwable) } ?: throwable
            }
        }

        for (node in takenNodes) {
            node.dispose()
        }

        if (failure != null) throw failure
    }
}
