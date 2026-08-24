package dev.deftu.stateful.core

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
internal object Runtime {
    private val lock = SynchronizedObject()

    // All guarded by `lock`.
    private val pending = mutableListOf<Node<*>>()
    private var batchDepth = 0
    private var flushing = false

    inline fun <T> locked(block: () -> T): T = synchronized(lock) { block() }

    /** Assumes the lock is held. */
    fun enqueue(node: Node<*>) {
        if (!pending.contains(node)) pending.add(node)
    }

    /**
     * Resolves pending effects and dispatches their bodies with the lock released.
     *
     * Safe to call from anywhere; re-entrant calls return immediately and let the outermost call
     * drain the queue, so an effect that writes a source does not recurse into a second flush.
     */
    fun flush() {
        val shouldFlush = locked {
            if (batchDepth > 0 || flushing) return@locked false
            flushing = true
            true
        }
        if (!shouldFlush) return

        var failure: Throwable? = null
        try {
            while (true) {
                val batch = locked {
                    if (pending.isEmpty()) return@locked null

                    val taken = pending.toList()
                    pending.clear()
                    taken.filter { node -> node.takeIfDirty() }
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
            locked { flushing = false }
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
