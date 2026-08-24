package dev.deftu.stateful.core

/**
 * The per-thread reactive context: which computation is currently running, and whether reads
 * are being tracked at all.
 *
 * Held in a thread-local because the graph is thread-safe and two threads may be recomputing
 * different nodes at once. On JS and wasm there is only ever one thread, so the actual is a
 * plain global and costs nothing.
 */
internal class Tracking {
    /** The computation whose dependencies tracked reads are currently being collected into. */
    var computation: Node<*>? = null

    /** Set by `untracked { }`. Suppresses dependency registration without unsetting [computation]. */
    var suppressed: Boolean = false

    inline fun <T> withComputation(node: Node<*>?, block: () -> T): T {
        val previous = computation
        computation = node
        try {
            return block()
        } finally {
            computation = previous
        }
    }

    inline fun <T> suppressing(block: () -> T): T {
        val previous = suppressed
        suppressed = true
        try {
            return block()
        } finally {
            suppressed = previous
        }
    }
}

/** The calling thread's [Tracking]. */
internal expect val tracking: Tracking
