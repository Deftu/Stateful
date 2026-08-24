package dev.deftu.stateful

/**
 * Decides when and where an effect body runs.
 *
 * The graph resolves under its lock and hands finished effect bodies here, so a scheduler is free
 * to defer, batch, or marshal them onto another thread. This is what makes the UI adapters
 * possible: a retained-mode toolkit needs its render thread, Compose has a frame clock, React
 * wants effects folded into its own scheduler.
 *
 * A scheduler is chosen per root and inherited by every computation under it.
 */
public fun interface Scheduler {
    /** Runs [task] at whatever point this scheduler considers correct. */
    public fun schedule(task: () -> Unit)

    public companion object {
        /**
         * Runs the task on the calling thread, before returning. The default.
         *
         * Effects still run with the runtime lock released, so this is safe — it is immediate,
         * not reentrant into the graph's critical section.
         */
        public val Immediate: Scheduler = Scheduler { task -> task() }
    }
}
