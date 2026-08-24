package dev.deftu.stateful.internal

import dev.deftu.stateful.Owner

/**
 * The per-thread reactive context: which computation is currently running, and whether reads
 * are being tracked at all.
 *
 * Held in a thread-local because the graph is thread-safe and two threads may be recomputing
 * different nodes at once. On JS and wasm there is only ever one thread, so the actual is a
 * plain global and costs nothing.
 */
internal class StateTracking {
    /** The computation whose dependencies tracked reads are currently being collected into. */
    var computation: StateNode<*>? = null

    /** Set by `untracked { }`. Suppresses dependency registration without unsetting [computation]. */
    var suppressed: Boolean = false

    /** The owner that computations created on this thread attach to. */
    var owner: Owner? = null

    /**
     * Whether this thread is already inside a flush.
     *
     * Re-entrancy is a per-thread property. A single shared flag would make one thread's flush
     * silently cancel another's, leaving that thread's effects queued and undispatched.
     */
    var flushing: Boolean = false

    inline fun <T> withComputation(node: StateNode<*>?, block: () -> T): T {
        val previous = computation
        computation = node
        try {
            return block()
        } finally {
            computation = previous
        }
    }

    inline fun <T> withOwner(next: Owner?, block: () -> T): T {
        val previous = owner
        owner = next
        try {
            return block()
        } finally {
            owner = previous
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

/** The calling thread's [StateTracking]. */
internal expect val tracking: StateTracking
