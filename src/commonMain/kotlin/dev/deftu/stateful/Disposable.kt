package dev.deftu.stateful

/**
 * Something with a lifetime that ends when told, rather than when collected.
 *
 * This library holds its graph strongly, so nothing is torn down by garbage collection. An effect,
 * an owner or a subscription runs until something disposes it — which is why an effect belongs to
 * an [Owner] and why dropping a handle without disposing it is a leak rather than a hint.
 */
public interface Disposable {
    /** Whether [dispose] has run. Disposing again does nothing. */
    public val isDisposed: Boolean

    /**
     * Ends this lifetime.
     *
     * Runs on the calling thread rather than through a [Scheduler], so a cleanup touching something
     * thread-affine — a graphics handle, a UI widget — must be disposed from a thread that may
     * touch it.
     */
    public fun dispose()
}
