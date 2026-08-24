package dev.deftu.stateful.internal

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Owns the graph lock and the pending-effect queue.
 *
 * One coarse reentrant lock guards all graph mutation and traversal. Fine-grained locking over a
 * graph traversed in dependency order invites deadlock, and computations are expected to be
 * cheap. The cost is real and worth stating plainly: every write, and every read of a non-clean
 * node, serializes globally. On JS and wasm the lock is a no-op, so none of this costs anything
 * on the browser adapters.
 *
 * Reentrancy is required, not incidental — resolving a memo reads other states while already
 * holding the lock.
 */
internal object StateGraph {
    private val lock = SynchronizedObject()

    // All guarded by `lock`.
    private val pending = mutableListOf<StateNode<*>>()
    private var batchDepth = 0

    inline fun <T> locked(block: () -> T): T = synchronized(lock) { block() }

    /** Assumes the lock is held. */
    fun enqueue(node: StateNode<*>) {
        if (!pending.contains(node)) pending.add(node)
    }

    /**
     * Resolves pending effects and dispatches their bodies with the lock released.
     *
     * Safe to call from anywhere. A re-entrant call on the same thread returns immediately and lets
     * the outermost one drain the queue, so an effect that writes a source does not recurse into a
     * second flush. The guard is per-thread: two threads may drain concurrently, each taking whole
     * batches out of the queue under the lock, because a shared guard would let one thread's flush
     * silently cancel another's and strand its effects.
     */
    fun flush() {
        val context = tracking
        if (context.flushing) return
        if (locked { batchDepth > 0 }) return

        context.flushing = true

        var failure: Throwable? = null
        try {
            while (true) {
                val batch = locked {
                    if (pending.isEmpty()) return@locked null

                    val taken = pending.toList()
                    pending.clear()

                    val ready = mutableListOf<StateNode<*>>()
                    for (node in taken) {
                        // A node whose dispatch is in flight keeps its dirty state and its place in
                        // the queue. Consuming that state here and then dropping the run as a
                        // duplicate would lose the change outright.
                        if (node.isDispatchPending) pending.add(node) else if (node.takeIfDirty()) ready.add(node)
                    }

                    // Null, not an empty list. Everything taken may have been deferred because a
                    // dispatch is in flight, and returning an empty batch would spin: the deferred
                    // nodes go straight back into the queue and the next turn takes them again.
                    // The flush that runs after each body picks them up instead.
                    if (ready.isEmpty()) null else ready
                }
                if (batch == null) break

                for (node in batch) {
                    try {
                        node.dispatch()
                    } catch (throwable: Throwable) {
                        if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                    }
                }
            }
        } finally {
            context.flushing = false
        }

        if (failure != null) throw failure
    }

    /**
     * Defers effect dispatch until the outermost call returns.
     *
     * Source writes still apply immediately, so a read inside the block sees pending writes.
     */
    fun <T> batch(block: () -> T): T {
        locked { batchDepth++ }
        try {
            return block()
        } finally {
            locked { batchDepth-- }
            flush()
        }
    }
}
